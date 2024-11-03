package org.techtown.gardes.main;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.techtown.gardes.main.BackPressCloseHandler;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;




public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 10; // 블루투스 활성화 상태
    private BluetoothAdapter bluetoothAdapter; // 블루투스 어댑터
    private Set<BluetoothDevice> devices; // 블루투스 디바이스 데이터 셋
    private BluetoothDevice bluetoothDevice; // 블루투스 디바이스
    private BluetoothSocket bluetoothSocket = null; //블루투스 소켓
    private OutputStream outputStream = null; //블루투스에 데이터를 출력하기 위한 출력 스트림
    private InputStream inputStream = null; //블루투스에 데이터를 입력하기 위한 입력 스트림
    private Thread workerThread = null; //문자열 수신에 사용되는 쓰레드
    private byte[] readBuffer; //수신된 문자열 저장 버퍼
    private int readBufferPosition; //버퍼  내 문자 저장 위치


    private TextView textView_pm25;
    private TextView textView_pm10;
    private TextView textView_Location;
    private TextView textView_conncetDevice;
    private TextView textview_state;
    private TextView textView_equal;
    private TextView power_onoff;
    private TextView textView_auto;

    private ToggleButton button_power;
    private ImageButton button_help;
    private ToggleButton button_auto;

    private Dialog dialog_help;
    private BackPressCloseHandler backkeyclickhandler;

    private ImageView nowstate;
    private ImageView pm10_state;
    private ImageView pm25_state;

    boolean connect_status;
    int pairedDeviceCount; //페어링 된 기기의 크기를 저장할 변수
    int pm25;
    int pm10;
    String[] array = {"0"};
    LinearLayout back;

    //지오코더 객체 생성

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //onCreate 함수. 아래에 추가할 내용을 적는다
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        backkeyclickhandler = new BackPressCloseHandler(this);

        //위치권한 허용 코드
        String[] permission_list = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        ActivityCompat.requestPermissions(MainActivity.this, permission_list, 1);


        //각 변수의 id를 메인 xml과 일치시키는 작업
        textView_pm10 = (TextView) findViewById(R.id.pm10);
        textView_pm25 = (TextView) findViewById(R.id.pm25);
        textView_conncetDevice = (TextView) findViewById(R.id.connectname);
        textView_Location = (TextView) findViewById(R.id.location);
        textview_state = (TextView) findViewById(R.id.state);
        textView_auto = (TextView) findViewById(R.id.auto_onoff);

        button_power = (ToggleButton) findViewById(R.id.power);
        button_auto = (ToggleButton) findViewById(R.id.auto);
        button_help = (ImageButton) findViewById(R.id.help);

        back = (LinearLayout) findViewById(R.id.background);
        nowstate = (ImageView) findViewById(R.id.nowstate);
        textView_equal = (TextView) findViewById(R.id.state_eval);
        power_onoff = (TextView) findViewById(R.id.power_onoff);

        pm10_state = (ImageView) findViewById(R.id.pm10image);
        pm25_state = (ImageView) findViewById(R.id.pm25image);

        dialog_help = new Dialog(MainActivity.this);
        dialog_help.requestWindowFeature(getWindow().FEATURE_NO_TITLE); //타이틀제거
        dialog_help.setContentView(R.layout.dialog);


        String deviceName = null;

        //블루투스 활성화 코드
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //블루투스 어댑터를 디폴트 어댑터로 설정

        if (bluetoothAdapter == null) { //기기가 블루투스를 지원하지 않을때
            Toast.makeText(getApplicationContext(), "Bluetooth 미지원 기기입니다.", Toast.LENGTH_SHORT).show();
            //처리코드 작성
        } else { // 기기가 블루투스를 지원할 때
            if (bluetoothAdapter.isEnabled()) { // 기기의 블루투스 기능이 켜져있을 경우
                selectBluetoothDevice(); // 블루투스 디바이스 선택 함수 호출
            } else { // 기기의 블루투스 기능이 꺼져있을 경우
                // 블루투스를 활성화 하기 위한 대화상자 출력
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                // 선택 값이 onActivityResult함수에서 콜백
                startActivityForResult(intent, REQUEST_ENABLE_BT);
                selectBluetoothDevice();
            }

        }


    }


    public void selectBluetoothDevice() {
        //이미 페어링 되어있는 블루투스 기기를 탐색
        devices = bluetoothAdapter.getBondedDevices();
        //페어링 된 디바이스 크기 저장
        pairedDeviceCount = devices.size();
        //페어링 된 장치가 없는 경우
        if (pairedDeviceCount == 0) {
            //페어링 하기 위한 함수 호출
            Toast.makeText(getApplicationContext(), "먼저 Bluetooth 설정에 들어가 페어링을 진행해 주세요.", Toast.LENGTH_SHORT).show();
        }
        //페어링 되어있는 장치가 있는 경우
        else {
            //디바이스를 선택하기 위한 대화상자 생성
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("페어링 된 블루투스 디바이스 목록");
            //페어링 된 각각의 디바이스의 이름과 주소를 저장
            List<String> list = new ArrayList<>();
            //모든 디바이스의 이름을 리스트에 추가
            for (BluetoothDevice bluetoothDevice : devices) {
                list.add(bluetoothDevice.getName());
            }
            list.add("취소");

            //list를 Charsequence 배열로 변경
            final CharSequence[] charSequences = list.toArray(new CharSequence[list.size()]);
            list.toArray(new CharSequence[list.size()]);

            //해당 항목을 눌렀을 때 호출되는 이벤트 리스너
            builder.setItems(charSequences, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //해당 디바이스와 연결하는 함수 호출
                    connectDevice(charSequences[which].toString());
                }
            });
            //뒤로가기 버튼 누를때 창이 안닫히도록 설정
            builder.setCancelable(false);
            //다이얼로그 생성
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }

    }

    @Override
    public void onBackPressed() { //뒤로가기 눌렀을때
        //super.onBackPressed();
        backkeyclickhandler.onBackPressed(); //2번누르면 종료
    }

    //연결 함수
    public void connectDevice(String deviceName) {
        //페어링 된 디바이스 모두 탐색
        for (BluetoothDevice tempDevice : devices) {
            //사용자가 선택한 이름과 같은 디바이스로 설정하고 반복문 종료
            if (deviceName.equals(tempDevice.getName())) {
                bluetoothDevice = tempDevice;
                break;
            }

        }
        Toast.makeText(getApplicationContext(), bluetoothDevice.getName() + " 연결 완료!", Toast.LENGTH_SHORT).show();
        //UUID생성
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        connect_status = true;
        //Rfcomm 채널을 통해 블루투스 디바이스와 통신하는 소켓 생성

        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();

            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            receiveData();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //상단에 연결된 디바이스 이름을 출력
        textView_conncetDevice.setText(bluetoothDevice.getName());
        //배경색 변경
        back.setBackgroundResource(R.drawable.gradient_good);
        /////////////////////////////////////////////////////////////////

        startLocationService();

        button_power.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            Toast.makeText(MainActivity.this, "ON", Toast.LENGTH_SHORT).show();
                            sendData("POWERON");
                            button_power.setBackgroundResource(R.drawable.power_on);
                            power_onoff.setText("켜짐");
                        } else {
                            Toast.makeText(MainActivity.this, "OFF", Toast.LENGTH_SHORT).show();
                            sendData("POWEROFF");
                            button_power.setBackgroundResource(R.drawable.power);
                            power_onoff.setText("꺼짐");
                        }
                    }
                }
        );


        button_auto.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked = true ) {
                            Toast.makeText(MainActivity.this, "ON", Toast.LENGTH_SHORT).show();
                            sendData("AUTOON");
                            button_auto.setBackgroundResource(R.drawable.auto_on);
                            textView_auto.setText("켜짐");

                            if(pm10<35 && pm25<35){ //미세먼지 농도가 좋음까지 도달하면
                                sendData("POWEROFF");
                                Toast.makeText(MainActivity.this, "공기 정화 완료!\n AUTO모드를 종료합니다.", Toast.LENGTH_SHORT).show();
                                isChecked = false;
                                button_auto.setBackgroundResource(R.drawable.auto);
                                textView_auto.setText("꺼짐");
                            }

                        } else {
                            Toast.makeText(MainActivity.this, "OFF", Toast.LENGTH_SHORT).show();
                            sendData("AUTOOFF");
                            button_auto.setBackgroundResource(R.drawable.auto);
                            textView_auto.setText("꺼짐");
                        }
                    }
                }

        );


        button_help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialoghelp();
            }
        });


    }


    public void receiveData() {
        final Handler handler = new Handler();
        //데이터 수신을 위한 버퍼 생성
        readBufferPosition = 0;
        readBuffer = new byte[1024];

        //데이터 수신을 위한 쓰레드 생성
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        //데이터 수신 확인
                        int byteAvailable = inputStream.available();
                        //데이터 수신 된 경우
                        if (byteAvailable > 0) {
                            //입력 스트림에서 바이트 단위로 읽어옴
                            byte[] bytes = new byte[byteAvailable];
                            inputStream.read(bytes);
                            //입력 스트림 바이트를 한 바이트씩 읽어옴
                            for (int i = 0; i < byteAvailable; i++) {
                                byte tempByte = bytes[i];
                                //개행문자를 기준으로 받음 (한줄)
                                if (tempByte == '\n') {
                                    //readBuffer 배열을 encodeBytes로 복사
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    //인코딩 된 바이트 배열을 문자열로 변환
                                    final String text = new String(encodedBytes, "UTF-8");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {

                                            array = text.split(",", 3);
                                            textView_pm25.setText(array[0]);
                                            textView_pm10.setText(array[1]);

                                            pm10=Integer.parseInt(array[1]);
                                            pm25=Integer.parseInt(array[0]);

                                            //텍스트뷰에 출력

                                            //공기 매우나쁨 76~
                                            int cai=(pm10>pm25)?pm10:pm25;

                                            if (cai>75) {
                                                nowstate.setImageResource(R.drawable.sobad2);
                                                nowstate.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                                                textview_state.setText("최악");
                                                textView_equal.setText("지금 당장 공기정화 필요");
                                                back.setBackgroundResource(R.drawable.gradient_bad);
                                            }
                                            //공기 나쁨 36-75
                                            if (cai>36 && cai<= 75) {
                                                nowstate.setImageResource(R.drawable.angry2);
                                                nowstate.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                                                textview_state.setText("나빠요");
                                                textView_equal.setText("공기정화가 필요해요");
                                                back.setBackgroundResource(R.drawable.gradient_bad);
                                            }
                                            //공기 그저그럼 16-35
                                            if (cai <= 35 && cai > 15) {
                                                nowstate.setImageResource(R.drawable.soso);
                                                nowstate.setScaleType(ImageView.ScaleType.CENTER);
                                                textview_state.setText("보통");
                                                textView_equal.setText("더 좋은 공기를 위해 정화 필요");
                                                back.setBackgroundResource(R.drawable.gradient_soso);
                                            }
                                            //공기 짱좋음 0-15
                                            if (cai <= 15 && cai >= 0) { // 0~15 공기좋음
                                                nowstate.setImageResource(R.drawable.smile3);
                                                textview_state.setText("훌륭함");
                                                textView_equal.setText("공기정화 할 필요없음");
                                                back.setBackgroundResource(R.drawable.gradient_good);
                                            }
                                            ///////////////////////////////////////////////
                                            if (pm25 > 75) {
                                                pm25_state.setImageResource(R.drawable.sobad2);
                                            }
                                            if (pm25>36 && pm25<= 75) {//공기 나쁨 36-75
                                                pm25_state.setImageResource(R.drawable.angry2);
                                            }
                                            //공기 그저그럼 35-75
                                            if (pm25 <= 35 && pm25 > 15) {  //공기 그저그럼 16-35
                                                pm25_state.setImageResource(R.drawable.soso);
                                            }
                                            //공기 짱좋음
                                            if (pm25 <= 15 && pm25 >= 0) { // 0~35 공기좋음
                                                pm25_state.setImageResource(R.drawable.smile3);
                                            }



                                            //////////////////////////////////////////////////
                                            if (pm10 > 75) {
                                                pm10_state.setImageResource(R.drawable.sobad2);
                                            }
                                            if (pm10>36 && pm10<= 75) {
                                                pm10_state.setImageResource(R.drawable.angry2);
                                            }
                                            //공기 그저그럼 35-75
                                            if (pm10 <= 35 && pm10 > 15) {
                                                pm10_state.setImageResource(R.drawable.soso);
                                            }
                                            //공기 짱좋음
                                            if (pm10 <= 15 && pm10 >= 0) {
                                                pm25_state.setImageResource(R.drawable.smile3);
                                            }
                                        }
                                    });
                                } // 개행문자가 아닐경우
                                else {
                                    readBuffer[readBufferPosition++] = tempByte;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();

                    }
                }
                try {
                    //1초 마다 받아옴
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        workerThread.start();
    }

    void sendData(String text) {
        //문자열에 개행 문자 추가
        text += "\n";
        try {
            //데이터 송신
            outputStream.write(text.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLocationService() {

        // get manager instance
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // set listener
        GPSListener gpsListener = new GPSListener();
        long minTime = 10000;
        float minDistance = 0;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, //실내에서 사용하므로 GPS가 아닌 3G로 값을 받아온다
                minTime,
                minDistance,
                gpsListener);
    }


    private class GPSListener implements LocationListener {

        public void onLocationChanged(Location location) {
            //capture location data sent by current provider
            Double latitude = location.getLatitude();
            Double longitude = location.getLongitude();

            String msg = getCurrentaddress(latitude, longitude);
            Log.i("GPSLocationService", msg);
            textView_Location.setText(msg);
            //경도위도 -> 주소변환


        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

    }

    public String getCurrentaddress(double latitude, double longtitude) {

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        List<Address> addresses;

        try {
            addresses = geocoder.getFromLocation(latitude, longtitude, 7);
        } catch (IOException e) {
            Toast.makeText(this, "지오코더 서비스 사용 불가", Toast.LENGTH_LONG).show();
            return "지오코더 서비스 사용불가";
        } catch (IllegalArgumentException i) {
            Toast.makeText(this, "잘못된 GPS 좌표", Toast.LENGTH_LONG).show();
            return "잘못된 GPS 좌표";
        }
        return addresses.get(0).getAdminArea() + " " + addresses.get(0).getLocality() + " "
                + addresses.get(0).getSubLocality();
    }

    public void showDialoghelp() {


        WindowManager.LayoutParams Params = dialog_help.getWindow().getAttributes();
        Params.width = WindowManager.LayoutParams.MATCH_PARENT;
        Params.height = WindowManager.LayoutParams.MATCH_PARENT;
        ImageButton ExitButton = dialog_help.findViewById(R.id.exit);
        dialog_help.getWindow().setAttributes((WindowManager.LayoutParams) Params);
        dialog_help.show();
        dialog_help.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));


        ExitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog_help.dismiss(); //다이얼로그 닫기
            }
        });

    }
}