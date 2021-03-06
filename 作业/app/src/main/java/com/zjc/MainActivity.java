package com.zjc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public double result;

    private TextView mTvShow;
    private SensorManager mSensorManager;

    private Button button1,button2;
    private TextView textView1,textView2;
    private Camera m_camera;
    private Switch m_switch;
    private Boolean isFirstStart = true;//??????????????????????????????
    private int requestCameraPermisson;

    private TextView batterLevel;
    private BroadcastReceiver batteryLevelRcvr;
    private IntentFilter batteryLevelFilter;


    public static String productKey;
    public static  String deviceName;
    public static String deviceSecret;
    public static String regionId;

    private static String pubTopic;
    private static String setTopic;

    private static final String payloadJson =
            "{"+
                    "\"id\": %s,"+
                    "\"params\": {"+
                    "\"Speed\": %s,},"+
                    "\"method\": \"thing.event.property.post\"}";
    private static MqttClient mqttClient;

    Time time = new Time();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initView ?????????????????????
        initView();

        //??????????????????
        mTvShow = (TextView) findViewById(R.id.tv_show);
        // ??????????????????????????????
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // ??????????????????????????????
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // ???????????????
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);


        //???????????????
        isFirstStart=true;
        //??????2?????????=?????????
        button2.setEnabled(false);
        button2.setText("???????????? IOT STUDIO ????????????");
        //EventBus
        EventBus.getDefault().register(this);

        //????????????
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
            button1.setEnabled(false);
            button2.setEnabled(false);
            m_switch.setEnabled(false);
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA},requestCameraPermisson);
        }

        //??????properties??????????????????
        ResourceBundle resource=ResourceBundle.getBundle("assets/thing");
        productKey=resource.getString("productKey");
        deviceName=resource.getString("deviceName");
        deviceSecret=resource.getString("deviceSecret");
        regionId=resource.getString("regionId");
        pubTopic= "/sys/" + productKey + "/" + deviceName + "/thing/event/property/post";
        setTopic="/sys/" + productKey + "/" + deviceName + "/thing/service/property/set";
    }

    public void initView() {
        button1 = findViewById(R.id.bt1);
        button1.setOnClickListener(new myButton1());
        button2 = findViewById(R.id.bt2);
        button2.setOnClickListener(new myButton2());

        m_switch = findViewById(R.id.switch2);
        m_switch.setOnCheckedChangeListener(new mySwitch());
        textView1 = findViewById(R.id.tv7);
        textView2 = findViewById(R.id.tv10);
    }

        @Override
        public void onSensorChanged (SensorEvent event){
            // ????????????????????????
            float[] values = event.values;
            result = Math.sqrt(Math.pow(Math.abs(values[0]), 2) + Math.pow(Math.abs(values[1]), 2) + Math.pow(Math.abs(values[2]), 2));
            StringBuffer buffer = new StringBuffer();
            buffer.append("??????????????????").append(result).append("\n");
            mTvShow.setText(buffer);
        }

        @Override
        public void onAccuracyChanged (Sensor sensor,int accuracy){
            return;
        }

    private class myButton1 implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            initAliyunIoTClient();
            getDeviceOrder();
            button1.setEnabled(false);
            button1.setText("????????????IOT STUDIO");
            button2.setEnabled(true);
            button2.setText("?????????IOT STUDIO ?????????");
        }
    }

    private class myButton2 implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            try {
                if (mqttClient.isConnected()){
                    mqttClient.disconnect();
                    Toast.makeText(getApplicationContext(),"???????????????",Toast.LENGTH_SHORT).show();
                    button1.setEnabled(true);
                    button1.setText("????????????IOT STUDIO");
                    button2.setEnabled(false);
                    button2.setText("?????????IOT STUDIO ????????????");

                    //?????????????????????topic???????????????
                    textView1.setText("??????????????????????????????");
                    textView2.setText("??????????????????????????????");
                }

            }catch (Exception e){
                e.printStackTrace();
                Toast.makeText(getApplicationContext(),"????????????????????????????????????",Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class mySwitch implements CompoundButton.OnCheckedChangeListener {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            //????????????????????????????????????
            if (!buttonView.isPressed()){
                return;
            }
            if (isChecked){
                autoSubmit();
            }else {
                return;
            }
        }
    }

    //????????????????????????????????????
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode==requestCameraPermisson){
            if (grantResults[0]== PackageManager.PERMISSION_GRANTED){
                button1.setEnabled(true);
                m_switch.setEnabled(true);
            }else {
                Toast.makeText(MainActivity.this,"???????????????????????????????????????",Toast.LENGTH_SHORT).show();
                button1.setEnabled(false);
                button2.setEnabled(false);
                m_switch.setEnabled(false);
            }
        }
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }

    //??????????????????
    private void autoSubmit(){

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what=0;
                mHandler.sendMessage(message);
            }
        },0,200);
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            if(msg.what == 0){
                String payload = postDeviceProperties(Double.toString(result));
                time.setToNow();
                textView1.setText(time.format("%Y-%m-%d %H:%M:%S") + "\n" + payload);
            }
        }
    };



    //????????????????????????
    private static void initAliyunIoTClient() {
        try {
            //??????????????????????????????????????????????????????id?????????????????????
            String clientId = "java" + System.currentTimeMillis();
            Map<String, String> params = new HashMap<>(16);
            params.put("productKey", productKey);
            params.put("deviceName", deviceName);
            params.put("clientId", clientId);
            String timestamp = String.valueOf(System.currentTimeMillis());
            params.put("timestamp", timestamp);
            // ????????????????????????????????????cn-shanghai
            String targetServer = "tcp://" + productKey + ".iot-as-mqtt."+regionId+".aliyuncs.com:1883";
            String mqttclientId = clientId + "|securemode=3,signmethod=hmacsha1,timestamp=" + timestamp + "|";
            String mqttUsername = deviceName + "&" + productKey;
            String mqttPassword = sign(params, deviceSecret, "hmacsha1");//????????????
            connectMqtt(targetServer, mqttclientId, mqttUsername, mqttPassword);
        } catch (Exception e) {
            System.out.println("initAliyunIoTClient error " + e.getMessage());
        }
    }

    //??????MQTT
    public  static void connectMqtt(String url,String clientId,String mqttUsername,String mqttPassword) throws Exception {
        MemoryPersistence persistence = new MemoryPersistence();
        mqttClient = new MqttClient(url,clientId,persistence);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setMqttVersion(4);
        connectOptions.setCleanSession(false);
        connectOptions.setAutomaticReconnect(false);
        connectOptions.setUserName(mqttUsername);
        connectOptions.setPassword(mqttPassword.toCharArray());
        connectOptions.setKeepAliveInterval(60);
        mqttClient.connect(connectOptions);
    }

//    //??????MQTT
//    public static void connectMqtt(String url, String clientId, String mqttUsername, String mqttPassword) throws Exception {
//        MemoryPersistence persistence = new MemoryPersistence();
//        mqttClient = new MqttClient(url, clientId, persistence);
//        //??????mqtt?????????????????????????????????????????????????????????????????????????????????????????????????????????
//        MqttConnectOptions connOpts = new MqttConnectOptions();
//        // MQTT 3.1.1??????MqttVersion(4)
//        connOpts.setMqttVersion(4);
//        connOpts.setAutomaticReconnect(false);
//        connOpts.setCleanSession(false);
//        //????????????
//        connOpts.setUserName(mqttUsername);
//        connOpts.setPassword(mqttPassword.toCharArray());
//        connOpts.setKeepAliveInterval(60);
//        //????????????
//        mqttClient.connect(connOpts);
//    }


    public static String sign(Map<String,String>params,String deviceSecret,String signMethod){
        String[] sortedKeys = params.keySet().toArray(new String[]{});
        Arrays.sort(sortedKeys);
        StringBuilder canonicalizedQueryString = new StringBuilder();
        for (String key: sortedKeys){
            if ("sign".equalsIgnoreCase(key)){
                continue;
            }
            canonicalizedQueryString.append(key).append(params.get(key));
        }
        try {
            String key = deviceSecret;
            return encryptHMAC(signMethod,canonicalizedQueryString.toString(),key);
        }catch (Exception e){
            throw  new RuntimeException(e);
        }
    }

    public static String encryptHMAC(String signMethod,String content,String key) throws Exception {
        SecretKey secretKey = new SecretKeySpec(key.getBytes("utf-8"),signMethod);
        Mac mac = Mac.getInstance(secretKey.getAlgorithm());
        mac.init(secretKey);
        byte[] data = mac.doFinal(content.getBytes("utf-8"));
        return bytesToHexString(data);
    }

    //???16??????
    public static final String bytesToHexString(byte[] bArray){
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i=0;i<bArray.length;i++){
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length()<2){
                sb.append(0);
            }
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    //??????????????????
    private static  String postDeviceProperties(String status){
        String payload= null ;
        try {
            payload = String.format(payloadJson,System.currentTimeMillis(),status);
            System.out.println("post:"+payload);
            MqttMessage message = new MqttMessage(payload.getBytes("utf-8"));
            message.setQos(1);
            /*
             * ?????????????????????????????????????????????QOS???????????????quality of service???
             * QOS=0?????????????????????????????????????????????????????????????????????????????????
             * QOS=1?????????????????????????????????????????????????????????????????????????????????
             * ???QOS=2???????????????????????????????????????????????????????????????
             */
            mqttClient.publish(pubTopic,message);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            return payload;
        }
    }

    //????????????
    private static void getDeviceOrder(){
        try{
            // ????????????
            mqttClient.setCallback(new MqttCallback() {
                //???????????????
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("LOG::????????????");
                    System.out.println("LOG::Reason="+cause);
                }
                //???????????????
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String str=new String(message.getPayload());
                    System.out.println("LOG::????????????="+str);
                    EventBus.getDefault().post(new MessageEvent(str));
                }
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("LOG::delivery complete");
                }
            });
            MqttTopic topic = mqttClient.getTopic(setTopic);
            int[] Qos = {1};
            String[] topic1 = {setTopic};
            mqttClient.subscribe(topic1,Qos);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //????????????
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void handleMessage(MessageEvent messageEvent) {
        System.out.println("LOG::"+messageEvent.getMessage());
        time.setToNow();
        textView2.setText(time.format("%Y-%m-%d %H:%M:%S")+"\n"+messageEvent.getMessage());
        String jsonData=messageEvent.getMessage();
        try {
            JSONObject jsonObject=new JSONObject(jsonData);
            Object deviceOrder=jsonObject.getJSONObject("params").get("LightSwitch");
            //ord????????????????????????????????? 1=?????????0=??????
            int ord=Integer.parseInt(deviceOrder.toString());
            if(ord==1){

            }
            else if(ord==0){
                return;
            }
            else {
                System.out.println("LOG::"+ord);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isFirstStart){
            try {
                if (mqttClient!= null){
                    if (mqttClient.isConnected()){
                        button1.setEnabled(true);
                        button2.setEnabled(true);
                        Toast.makeText(getApplicationContext(),"??????????????????????????????????????????",Toast.LENGTH_SHORT).show();
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            isFirstStart =false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Toast.makeText(getApplicationContext(),"????????????????????????",Toast.LENGTH_SHORT).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        try {
            if (mqttClient.isConnected()){
                mqttClient.close();
            }
            m_camera.release();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}