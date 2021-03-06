package com.communication.yang.cpe_2;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.SystemClock;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.communication.yang.cpe_2.adapter.HeartLogAdapter;
import com.communication.yang.cpe_2.adapter.RunningLogAdapter;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import org.litepal.LitePal;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import android_serialport_api.PackageSerialPort.EncapsulationFormat;
import db.InitDataBase;
import db.addressAndPort.PostalAddress;
import db.agreement.DataTransmissionProtocol;
import db.agreement.InitCpeDataField;
import db.agreement.InitializationConfiguration;
import db.analyticMethod.InitCpeAnalysis;
import db.log.HeartbeatLog;
import db.log.LogParameters;
import db.log.RunningLog;
import android_serialport_api.SerialPort;
import db.serialPort.ConnectionVerificationReceiveOne;
import db.serialPort.ConnectionVerificationReceiveTwo;
import db.serialPort.ConnectionVerificationResponse;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import queue.HttpQueue;
import queue.IssueQueue;
import queue.QueueSize;
import queue.SerialPortQueue;
import queue.UploadQueue;
import serviceHttp.FormPort;
import serviceHttp.HttpClient;
import serviceMqtt.IGetMqttClientMessageCallBack;
import serviceMqtt.MqttClientService;
import serviceTcp.IGetTcpClientMessageCallBack;
import serviceTcp.TcpClientService;
import serviceUdp.UdpService;
import tool.IntenetUtil;
import tool.SignalIntensity;
import serviceUdp.IGetUdpMessageCallBack;
import android_serialport_api.SerialPortUtils;
import tool.Timestamp;

public class MainActivity extends AppCompatActivity implements IGetMqttClientMessageCallBack, IGetUdpMessageCallBack, IGetTcpClientMessageCallBack {

    /**
     * 1.?????????????????????????????????????????????
     */
    IssueQueue issueQueue = IssueQueue.getInstance();

    /**
     * 2.???????????????????????????????????????????????????
     */
    SerialPortQueue serialPortQueue = SerialPortQueue.getInstance();

    /**
     * 3.???????????????????????????????????????
     */
    UploadQueue uploadQueue = UploadQueue.getInstance();

    /**
     * 4.HTTP????????????????????????????????????
     */
    HttpQueue httpQueue = HttpQueue.getInstance();

    /**
     * TCP???UDP???????????????
     */
    PostalAddress postalAddress = new PostalAddress();

    /**
     * HTTP ??????
     */
    private String url;

//    /**
//     * HTTP ??????
//     */
//    private String url1 = "http://192.168.1.152:8781/tianjinCpe_war_exploded/cpe/sync";


    private boolean http = false;

    //??????????????????
    private Integer resert = 0;

    //???????????????
    private Integer server = 0;

    /**
     * ????????????LogAdapter????????????ListView???
     */
//    List<String> list_runlog = new LinkedList<String>();
//
//    /**
//     * ????????????LogAdapter????????????ListView???
//     */
//    List<HeartbeatLog> list_heartlog = new LinkedList<HeartbeatLog>();
//
//    /**
//     *
//     */
//    RunningLogAdapter runningLogAdapter = null;
//
//    HeartLogAdapter heartLogAdapter = null;

    /**
     * ????????????
     */
    ServiceOperation serviceOperation;

    /**
     * ??????????????????
     */
    private boolean informationSafety = false;

    /**
     * 1.?????????????????????
     * 2.????????????????????????,?????????????????????????????????????????????
     */
    private String path = "/dev/ttyHSL1";
    private int baudrate = 115200;
    private int byteRate = 16000000;

    private SerialPort serialPort = null;
    private SerialPortUtils serialPortUtils = new SerialPortUtils();

    //?????????????????????
    /**
     * ?????????????????????????????? + 1
     */
    private Integer ack;

    /**
     * ??????????????????
     */
    private Integer seq = 0;

    /**
     * ???????????????????????????
     */
    private Integer cpe1_seq;

    OkHttpClient client = null;

    /**
     * ???????????????????????????
     */
    private Integer cpe1_2_seq;
    private Integer cpe1_2_ack;

    /**
     * ???????????? 1.MQTT 2.UDP 3.TCP
     */
    private Integer communication;

    /**
     * ????????????????????? true ???????????????, false ???????????????
     */
    private Boolean initResponse = true;

    /**
     * ??????????????????
     */
    SerialPortThread serialPortThread = new SerialPortThread();

    /**
     * ?????????????????????
     */
    Timer timerConnectionVerificationResponse = new Timer();

    /**
     * RunningLog?????????
     */
    // Timer timerRunningLog = new Timer();
    RunningLogTimer runningLogTimer = new RunningLogTimer();
    /**
     * HeartbeatLog?????????,??????????????????
     */
    Timer timerHeartbeatLog = new Timer();
    /**
     * ??????????????????????????????
     */
    SerialPortQueueThread serialPortQueueThread = new SerialPortQueueThread();
    /**
     * ????????????????????????
     */
    IssueQueueThread issueQueueThread = new IssueQueueThread();
    /**
     * ??????????????????
     */
    UploadQueueThread uploadQueueThread = new UploadQueueThread();


    /**
     * ?????????
     */
    EditText text;

    public void initThread() {
        Log.e("????????????", SignalIntensity.getSERIAL());

        /**
         * ?????????
         */
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);


        /**
         * ??????????????????
         */
        XXPermissions.with(this)
                .permission(Permission.READ_PHONE_STATE)
                .request(new OnPermission() {
                    @Override
                    public void hasPermission(List<String> granted, boolean isAll) {
                        if (isAll) {
                            LogParameters.Running(5, 3, "??????SIM???????????????", true);
                        }
                    }

                    @Override
                    public void noPermission(List<String> denied, boolean quick) {
                        if (quick) {
                            LogParameters.Running(5, 3, "?????????????????????,???????????????SIM?????????", false);
                            //??????????????????????????????????????????????????????????????????
                        } else {
                            LogParameters.Running(5, 3, "??????SIM???????????????", false);
                        }
                    }
                });


        LitePal.initialize(this);
        LitePal.getDatabase();
        InitDataBase.delete();

        serialPortThread.start(); //????????????
        serialPortQueueThread.start();//????????????
        issueQueueThread.start();//????????????

    }

    @SneakyThrows
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initThread();
    }

    @SneakyThrows
    @Override
    protected void onDestroy() {
        super.onDestroy();
        serviceOperation.unBind();
    }

    /**
     * MQTT????????????
     */
    @Override
    public void onMqttReceive(String topic, String message) {
        issueQueue.maxPut(message, QueueSize.MaxSize);
    }

    /**
     * UDP????????????
     */
    @Override
    public void onUdpReceive(String data) {
        issueQueue.maxPut(data, QueueSize.MaxSize);
    }

    /**
     * TCP????????????
     */
    @Override
    public void onTcpReceive(String data) {
        issueQueue.maxPut(data, QueueSize.MaxSize);
    }

    /**
     * MQTT????????????
     */
    @Override
    public void mqttServiceSecurity(boolean message) {
        this.informationSafety = message;
    }

    /**
     * UDP????????????
     */
    @Override
    public void udpServiceSecurity(boolean state) {
        this.informationSafety = state;
    }

    /**
     * TCP????????????
     */
    @Override
    public void tcpServiceSecurity(boolean state) {
        this.informationSafety = state;
    }


    /**
     * ????????????
     */
    class SerialPortThread extends Thread {
        @Override
        public void run() {
            serialPort = serialPortUtils.openSerialPort(path, baudrate, 0); //?????????????????????????????????????????????

            LogParameters.Running(0, 3,
                    "????????????????????????,??????????????????",
                    true
            );
            /**
             * ????????????????????????
             * @contentLenght ???????????????
             * @buffer ?????????
             * @cursor ????????????
             * @int headerLength ?????????
             */
            serialPortUtils.setOnDataReceiveListener(new SerialPortUtils.OnDataReceiveListener() {
                @Override
                public void onDataReceive(byte[] buffer, int contentLenght, int cursor, int headerLength) throws UnsupportedEncodingException {

                    byte[] bytes = new byte[contentLenght];

                    System.arraycopy(buffer, cursor + headerLength, bytes, 0, contentLenght);

                    String data = new String(bytes, "utf8");

                    Log.i("??????CRC??????????????????", data);

                    //CRC??????
                    if (EncapsulationFormat.judgeCRC(data)) {

                        String string = EncapsulationFormat.interceptDataBits(data); //???????????????

                        Log.i("??????CRC??????????????????", string);

                        serialPortQueue.maxPut(string, QueueSize.MaxSize);
                    }
                }
            });
        }
    }

    /**
     * ??????????????????
     */
    class ConnectionVerificationResponseTimer extends TimerTask {
        @Override
        public void run() {
            Log.d("Y-CPE2-V1.0????????????", "+" + ++seq);
            ConnectionVerificationResponse connectionVerificationResponse = new ConnectionVerificationResponse();
            connectionVerificationResponse.setSyn(1);
            connectionVerificationResponse.setAck(1);
            connectionVerificationResponse.setSeqq(seq);
            connectionVerificationResponse.setAckk(ack);

            issueQueue.maxPut(EncapsulationFormat.initializationEncapsulation
                    (InitCpeAnalysis.toJsonString(connectionVerificationResponse), 4), QueueSize.MaxSize);
        }
    }

    /**
     * RunningLog?????????
     * 9000ms -> 1???30?????????
     * ????????????????????????->??????->??????
     */
    class RunningLogTimer extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                Thread.sleep(10000);
                //??????
                List<RunningLog> runningLogs = LitePal.limit(10).find(RunningLog.class);
                if (runningLogs != null) {
                    for (int i = 0; i < runningLogs.size(); i++) {

                        String runningLog = InitCpeAnalysis.toJsonString(runningLogs.get(i));
                        //todo ????????????
                        String data = EncapsulationFormat.initializationEncapsulation(runningLog, 2);
                        //todo ????????????????????????
                        issueQueue.maxPut(data, QueueSize.MaxSize);

                        runningLogs.get(i).delete();
                    }
                }
            }
        }
    }


    /**
     * HeartbeatLog?????????,??????????????????
     * 3600000ms -> 1????????????
     * ?????????
     */
    class HeartbeatLogTimer extends TimerTask {
        @Override
        public void run() {

            final HeartbeatLog heartbeatLog = LogParameters.Heart(5, true, MainActivity.this);

            String data = EncapsulationFormat.initializationEncapsulation
                    (InitCpeAnalysis.toJsonString(heartbeatLog), 3);

            //????????????
            issueQueue.put(data);

            heartbeatLog.save();

        }
    }

    /**
     * ??????????????????????????????
     */
    class SerialPortQueueThread extends Thread {
        @SneakyThrows
        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            while (true) {
                try {
                    if (!serialPortQueue.empty()) {

                        String data = serialPortQueue.get();

                        //todo 1.??????????????????????????????????????????????????????????????????,?????????????????????????????????????????????
                        //1.???????????????
                        String dataBuffer = EncapsulationFormat.interceptData(data);
                        Log.e("???????????????", dataBuffer);

                        //2.??????????????????
                        Integer type =
                                EncapsulationFormat.messageTypeJudgment
                                        (EncapsulationFormat.interceptMessageType(data));


                        //TODO 2.????????????
                        SharedPreferences preferences = getSharedPreferences("data", MODE_PRIVATE);

                        SharedPreferences.Editor editor = preferences.edit();

                        switch (type) {
                        /*
                        ???????????????
                         */
                            case 0: {

                                //???????????????
                                InitCpeDataField initCpeDataField =
                                        InitCpeAnalysis.toJson(dataBuffer, InitCpeDataField.class);

//                            Log.e("????????????????????????????????????", String.valueOf(SystemClock.currentThreadTimeMillis()));

                                // Log.e("????????????????????????",initCpeDataField.getModernClock());

                                //??????????????????
//                            SystemClock.setCurrentTimeMillis(initCpeDataField.getModernClock());


                                LogParameters.Running(0, 2,
                                        "???????????????????????????????????????:" + initCpeDataField.getModernClock(),
                                        true
                                );


//                            Log.e("?????????????????????", String.valueOf(SystemClock.currentThreadTimeMillis()));

                                url = initCpeDataField.getUrl();

                                if (url != null) {
                                    LogParameters.Running(0, 3,
                                            "????????????HTTP??????" + url,
                                            true
                                    );
                                }

                                editor.putLong("timestamp", initCpeDataField.getTimestamp());
                                editor.putBoolean("judge", true);

                                communication = initCpeDataField.getProtocolType();

                                //Thread.sleep(2000);
                                //??????????????????,????????????
                                //- ????????????@hide????????????
                                //?????????????????????java?????????????????????????????????????????????
//                            ActivityManager mActivityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
//                            Method method = Class.forName("android.app.ActivityManager").getMethod("forceStopPackage", String.class);
//                            method.invoke(mActivityManager,"com.sgcc.vpn_client");  //packageName??????????????????????????????????????????
//
//                            Thread.sleep(10000);
//
//                            //??????VPN??????
//                            PackageManager packageManager = getPackageManager();
//                            Intent intent = packageManager.getLaunchIntentForPackage("com.sgcc.vpn_client");
//                            startActivity(intent);


                                //TODO ????????????????????????
                                if (communication != null) {
                                    switch (communication) {
                                        case 1: {
                                            //????????????
                                            editor.putString("host", initCpeDataField.getHost());
                                            editor.putString("username", initCpeDataField.getUsername());
                                            editor.putString("password", initCpeDataField.getPassword());

                                            if (initCpeDataField.getClientId() != null) {
                                                editor.putString("clientId", initCpeDataField.getClientId());
                                            }

                                            //List?????????JSON?????????????????????????????????????????????topicNameList????????????topicList
                                            editor.putString("topicNameList",
                                                    InitCpeAnalysis.toJsonString(initCpeDataField.getTopicList())
                                            );

                                            Log.e("list??????????????????",
                                                    InitCpeAnalysis.toJsonString(initCpeDataField.getTopicList()));

                                            editor.apply();

                                            //TODO ??????MQTT????????????
                                            serviceOperation = new ServiceOperation
                                                    (MainActivity.this, 1);
                                            serviceOperation.serviceOnCreate();
                                            break;
                                        }

                                        case 2: {
                                            postalAddress.setIp(initCpeDataField.getIp());
                                            postalAddress.setPort(initCpeDataField.getPort());

                                            editor.putString("ip", initCpeDataField.getIp());
                                            editor.putInt("port", initCpeDataField.getPort());
                                            editor.apply();
                                            //TODO ??????UDP????????????
                                            serviceOperation = new ServiceOperation
                                                    (MainActivity.this, 2);
                                            serviceOperation.serviceOnCreate();
                                            break;
                                        }

                                        case 3: {

                                            postalAddress.setIp(initCpeDataField.getIp());
                                            postalAddress.setPort(initCpeDataField.getPort());

                                            editor.putString("ip", initCpeDataField.getIp());
                                            editor.putInt("port", initCpeDataField.getPort());
                                            editor.apply();
                                            //TODO ??????TCP????????????
                                            serviceOperation = new ServiceOperation
                                                    (MainActivity.this, 3);
                                            serviceOperation.serviceOnCreate();
                                            break;
                                        }

                                        //HTTP????????????????????????
                                        case 4: {
                                            http = true;
                                            informationSafety = true;
                                            LogParameters.Running(0, 1,
                                                    "??????HTTP??????:????????????",
                                                    true
                                            );
                                            break;
                                        }

                                        default: {
                                            LogParameters.Running(0, 3,
                                                    "protocolType??????:??????",
                                                    false
                                            );
                                            break;
                                        }
                                    }
                                }
                                break;

                            }
                        /*
                        ?????????????????????
                        */
                            case 1: {
                                //??????????????????
                                uploadQueue.maxPut(dataBuffer, QueueSize.MaxSize);
                                Log.d("1", "----------------------");
                                break;
                            }
                        /*
                        ??????????????????
                         */
                            case 4: {
                                if (initResponse) {
                                    ConnectionVerificationReceiveOne connectionVerificationReceiveOne =
                                            InitCpeAnalysis.toJson(dataBuffer, ConnectionVerificationReceiveOne.class);

                                    Integer SYN = connectionVerificationReceiveOne.getSyn();
                                    Integer seq = connectionVerificationReceiveOne.getSeq();

                                    Log.d("???????????????", "SYN:" + SYN);
                                    Log.d("???????????????", "seq:" + seq);

                                    LogParameters.Running(0, 3,
                                            "???????????????,SYN???" + SYN + "\n" +
                                                    "???????????????,seq:" + seq,
                                            true
                                    );

                                    initResponse = false;

                                    if (SYN == 1) {
                                        cpe1_seq = seq; //???????????????????????????
                                        ack = seq + 1; //???????????????ack??????
                                        //???????????????
                                        timerConnectionVerificationResponse.schedule
                                                (new ConnectionVerificationResponseTimer(), 10000, 90000);
                                    }
                                } else {
                                    ConnectionVerificationReceiveTwo connectionVerificationReceiveTwo =
                                            InitCpeAnalysis.toJson(dataBuffer, ConnectionVerificationReceiveTwo.class);

                                    Integer ACK = connectionVerificationReceiveTwo.getAck();
                                    cpe1_2_seq = connectionVerificationReceiveTwo.getSeqq();
                                    cpe1_2_ack = connectionVerificationReceiveTwo.getAckk();

                                    Log.d("???????????????", "ACK:" + ACK);
                                    Log.d("???????????????", "seq:" + cpe1_2_seq);
                                    Log.d("???????????????", "ack:" + cpe1_2_ack);

                                    Log.d("??????????????????seq", String.valueOf(seq));

                                    LogParameters.Running(0, 3,
                                            "???????????????,ACK:" + ACK + "\n" +
                                                    "???????????????,seq:" + cpe1_2_seq + "\n" +
                                                    "???????????????,ack:" + cpe1_2_ack + "\n" +
                                                    "??????????????????seq:" + seq,
                                            true
                                    );

                                    initResponse = true;

                                    if (ACK == 1 && cpe1_2_seq == ack && cpe1_2_ack == seq + 1) {
                                        //?????????????????????????????????????????????CPE1????????????????????????????????????????????????
                                        timerConnectionVerificationResponse.cancel(); //??????
                                        timerConnectionVerificationResponse = null;


                                        LogParameters.Running(0, 3,
                                                "TY-CPE1-V2.0:??????TY-CPE1-V1.0?????????TY-CPE2-V2.0??????",
                                                true
                                        );

                                        //timerRunningLog.schedule(new RunningLogTimer(), 1000, 1000);

                                        runningLogTimer.start();

                                        timerHeartbeatLog.schedule(new HeartbeatLogTimer(), 1000, 300000);

                                        uploadQueueThread.start();

                                        InitializationConfiguration initializationConfiguration =
                                                new InitializationConfiguration("OK", SignalIntensity.getSERIAL());


                                        String init = EncapsulationFormat.initializationEncapsulation
                                                (InitCpeAnalysis.toJsonString(initializationConfiguration), 0);

                                        issueQueue.maxPut(init, QueueSize.MaxSize);
                                    }
                                }
                                break;
                            }
                        /*
                         ????????????
                         */
                            default: {
                                Log.e("SerialPortQueueThread", "????????????");
                                LogParameters.Running(0, 3,
                                        "?????????????????????????????????",
                                        false
                                );
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ????????????????????????
     */
    class IssueQueueThread extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                try {
                    if (!issueQueue.empty() && serialPort != null) {
                        String str = issueQueue.get();

                        if (str.length() > byteRate) {
                            LogParameters.Running(6, 3,
                                    "??????????????????,??????",
                                    false
                            );
                        } else {
                            serialPortUtils.sendSerialPort(str);
                            Log.d("????????????", str);
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ??????????????????
     */
    class UploadQueueThread extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                try {
                    if (!uploadQueue.empty()) {
                        String data = uploadQueue.get();
                        if (!data.equals("")) {
                            DataTransmissionProtocol dataTransmissionProtocol =
                                    InitCpeAnalysis.toJson(data, DataTransmissionProtocol.class);

                            Integer protocolType = dataTransmissionProtocol.getProtocolType();

                            data = dataTransmissionProtocol.getContent();
                            String cpeTwoUid = dataTransmissionProtocol.getCpeTwoUid();

                            //??????????????????
                            if (SignalIntensity.getSERIAL().equals(cpeTwoUid) && protocolType != null && communication != null) {
                                switch (protocolType) {
                                    //MQTT
                                    case 1: {

                                        if (communication == 1) {
                                            MqttClientService.send
                                                    (dataTransmissionProtocol.getTopicName(),
                                                            data, dataTransmissionProtocol.getQos());
                                            Log.e("????????????", data);
                                        }

                                        break;
                                    }
                                    //UDP
                                    case 2: {
                                        if (communication == 2) {
                                            UdpService.send
                                                    (data);
                                            Log.e("????????????", data);
                                            break;
                                        }
                                    }
                                    //TCP
                                    case 3: {
                                        if (communication == 3)
                                            TcpClientService.send
                                                    (data);
                                        Log.e("????????????", data);
                                        break;
                                    }
                                    //HTTP,???????????????
                                    case 4: {
                                        if (communication == 4) {
                                            if (http) {

                                                Log.e("?????????", "HTTP,???????????????");
                                                long id = dataTransmissionProtocol.getMid();

                                                if (client == null) {
                                                    client = new OkHttpClient.Builder()
                                                            .connectTimeout(1, TimeUnit.SECONDS)
                                                            .readTimeout(2, TimeUnit.SECONDS)
                                                            .build();
                                                }

                                                HashMap<String, String> hasHMap = new HashMap<>();
                                                hasHMap.put("content", data);

                                                Log.e("??????", data);

                                                Request request;

                                                Response response = null;


//                                  Log.e("????????????",text.getText().toString());

                                                if (url != null) {
                                                    request = FormPort.request(url, hasHMap);

                                                    if (request != null) {
                                                        //??????
                                                        try {
                                                            response = client.newCall(request).execute();
                                                        } catch (Exception e) {
                                                            //??????
                                                            switch (resert) {
                                                                case 0: {
                                                                    //????????????
                                                                    LogParameters.Running(4, 3,
                                                                            "????????????:" + e.getMessage(),
                                                                            false
                                                                    );
                                                                    resert = 1;
                                                                    break;
                                                                }
                                                                case 1: {
                                                                    Log.e("1", "??????");
                                                                    //????????????
                                                                    break;
                                                                }
                                                            }
                                                        }

                                                        if (response != null) {
                                                            if (response.isSuccessful()) {
                                                                server = 0;
                                                                resert = 0;
                                                                String string = response.body().string();

                                                                Log.e("??????", string);

                                                                //????????????
                                                                dataTransmissionProtocol.setMid(id);
                                                                dataTransmissionProtocol.setCpeTwoUid(SignalIntensity.getSERIAL());
                                                                dataTransmissionProtocol.setContent(string);
                                                                dataTransmissionProtocol.setProtocolType(4);

                                                                //????????????
                                                                string = InitCpeAnalysis.toJsonString(dataTransmissionProtocol);

                                                                issueQueue.maxPut
                                                                        (EncapsulationFormat.initializationEncapsulation(string, 1),
                                                                                QueueSize.MaxSize);

                                                            } else {
                                                                //???????????????
                                                                switch (server) {
                                                                    case 0: {
                                                                        //???????????????
                                                                        LogParameters.Running(4, 3,
                                                                                "??????????????????????????????????????????",
                                                                                false
                                                                        );
                                                                        server = 1;
                                                                        break;
                                                                    }
                                                                    case 1: {
                                                                        Log.e("?????????", "!");
                                                                        //????????????
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                            }
                                        }

                                    }
                                }
                            } else {
                                LogParameters.Running(4, 3,
                                        "??????????????????,????????????" + SignalIntensity.getSERIAL() + "," + "???????????????" + cpeTwoUid,
                                        false
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
