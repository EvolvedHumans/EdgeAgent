package com.communication.yang.cpe_1;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.communication.yang.cpe_1.adapter.HeartLogAdapter;
import com.communication.yang.cpe_1.adapter.RunningLogAdapter;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import org.litepal.LitePal;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android_serialport_api.PackageSerialPort.EncapsulationFormat;
import db.InitDataBase;
import db.agreement.InitializationConfiguration;
import db.analyticMethod.InitCpeAnalysis;
import db.log.LogParameters;
import db.log.RunningLog;
import db.log.HeartbeatLog;
import db.serialPort.ConnectionVerificationPublishOne;
import db.serialPort.ConnectionVerificationPublishTwo;
import db.serialPort.ConnectionVerificationReceive;
import http.HttpClient;
import android_serialport_api.SerialPort;
import lombok.SneakyThrows;
import queue.CloudDataQueue;
import queue.HeartbeatQueue;
import queue.QueueSize;
import serviceMqtt.IGetMqttClientMessageCallBack;
import serviceMqtt.MqttClientService;
import serviceMqtt.MqttClientServiceConnection;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import serviceMqtt.MqttConfig;
import serviceMqtt.SgitTopic;
import tool.IntenetUtil;
import queue.IssueQueue;
import queue.SerialPortQueue;
import tool.SignalIntensity;
import android_serialport_api.SerialPortUtils;

public class MainActivity extends AppCompatActivity implements IGetMqttClientMessageCallBack, View.OnClickListener {

    /**
     * 1.?????????????????????service??????????????????
     */
    IssueQueue issueQueue = IssueQueue.getInstance();

    /**
     * 2.?????????????????????????????????????????????
     */
    SerialPortQueue serialPortQueue = SerialPortQueue.getInstance();

    /**
     * 3.MQTT???????????????????????????
     */
    CloudDataQueue cloudDataQueue = CloudDataQueue.getInstance();

    /**
     * ????????????LogAdapter????????????ListView???
     */
    List<String> list_runlog = new LinkedList<>();

    /**
     * ????????????LogAdapter????????????ListView???
     */
    List<HeartbeatLog> list_heartlog = new LinkedList<>();

    /**
     * ?????????????????????,??????????????????
     */
    String heartbeatLogUrl = MqttConfig.mHeartHost();
    HeartbeatQueue heartbeatQueue = HeartbeatQueue.getInstance();

    /**
     * ?????????????????????????????????
     */
    String runningLogUrl = MqttConfig.mRunningHost();

    /**
     * COM6??????
     */
    private String path = "/dev/ttyHSL1";
    private int baudrate = 115200;

    /**
     * ???????????????1.???????????? 2.????????????  //??????????????????????????????
     */
    private SerialPortUtils serialPortUtils = new SerialPortUtils();
    private SerialPort serialPort = null;

    /**
     * ??????????????????????????????
     */
    private Integer seq = 0;

    /**
     * ?????????????????????
     */
    private boolean allowToSend = false;

    /**
     * ????????????
     */
    BindService bindService = null;

    /**
     * JSON????????????
     */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * ???????????????
     */
    //???????????????
    RunningLogAdapter runningLogAdapter = null;

    /*
    ???????????????
     */
    //???????????????
    HeartLogAdapter heartLogAdapter = null;

    /**
     * ??????????????????
     */
    //1.????????????
    SerialPortThread serialPortThread = new SerialPortThread();
    //2.?????????????????????
    Timer timerConnection = new Timer();
    //3.??????????????????????????????
    SerialPortQueueThread serialPortQueueThread = new SerialPortQueueThread();
    //4.????????????????????????
    CloudDataQueueThread cloudDataQueueThread = new CloudDataQueueThread();
    //5.Mqtt??????????????????
    IssueQueueThread issueQueueThread = new IssueQueueThread();
    //6.????????????
    HeartbeatLogThread heartbeatLogThread = new HeartbeatLogThread();
    //7.????????????
    Timer timerDataBase = new Timer();
    //8.??????????????????
    Timer timerRunningLog = new Timer();
    //9.????????????
    Timer timerHeartbeatLog = new Timer();

    //mqtt?????????????????????
    Integer mqtt = 0;

    //????????????????????????
    Integer serial = 0;


    public void init() {
        /**
         * ???????????????????????????????????????
         */
        getWindow().setFlags
                (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        /**
         * ??????????????????
         */
        XXPermissions.with(this)
                .permission(Permission.READ_PHONE_STATE)
                .request(new OnPermission() {
                    @Override
                    public void hasPermission(List<String> granted, boolean isAll) {
                        if (isAll) {
                            LogParameters.Running(5, "??????SIM???????????????", true);
                        }
                    }

                    @Override
                    public void noPermission(List<String> denied, boolean quick) {
                        if (quick) {
                            LogParameters.Running(5, "?????????????????????,???????????????SIM?????????", false);
                            //??????????????????????????????????????????????????????????????????
                        } else {
                            LogParameters.Running(5, "??????SIM???????????????", false);
                        }
                    }
                });


        LitePal.initialize(this);
        LitePal.getDatabase();
        InitDataBase.delete();

        serialPortThread.start();

        timerConnection.schedule
                (new ConnectionVerificationTimer(), 10000, 60000);

        serialPortQueueThread.start();

        cloudDataQueueThread.start();

        issueQueueThread.start();

        heartbeatLogThread.start();

        timerDataBase.schedule
                (new InitDataBaseTimer(), 9000000, 9000000);

        timerRunningLog.schedule
                (new RunningLogTimer(), 1000, 1000);

        timerHeartbeatLog.schedule
                (new HeartbeatLogTimer(), 60000, 300000);
    }

    @SneakyThrows
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    /**
     * MQTT????????????
     */
    @Override
    public void setMessage(String topic, String message) {

        Log.e("CPE1??????????????????(????????????????????????)", String.valueOf(++mqtt));

        //????????????issueQueue
        issueQueue.maxPut(message, QueueSize.MaxSize);
    }

    /**
     * ??????MQTT?????????????????????
     */
    @Override
    public void setAllowToSend(boolean allowToSend) {
        this.allowToSend = allowToSend;
    }

    /**
     * ???????????????
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonPanel: {
                View view =
                        getLayoutInflater().inflate
                                (R.layout.popwindow, null, false);

                PopupWindow popupWindow = new PopupWindow
                        (view, ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT, true);

                //?????????ID???R.id.buttonPanel?????????,x????????? 0,y????????? 0
                popupWindow.showAsDropDown(findViewById(R.id.buttonPanel), 0, 0);

                popupWindow.setFocusable(true);

                ListView listView = view.findViewById(R.id.list_item);
                //???????????????
                if (runningLogAdapter == null) {
                    runningLogAdapter =
                            new RunningLogAdapter(MainActivity.this, R.layout.runninglog_listview, list_runlog);
                }
                listView.setAdapter(runningLogAdapter);


                ListView listView1 = view.findViewById(R.id.list_item_1);
                //???????????????
                if (heartLogAdapter == null) {
                    heartLogAdapter =
                            new HeartLogAdapter(MainActivity.this, R.layout.heartlog_listview, list_heartlog);
                }

                listView1.setAdapter(heartLogAdapter);
            }
        }
    }

    class SerialPortThread extends Thread {
        @Override
        public void run() {
            serialPort = serialPortUtils.openSerialPort(path, baudrate);
            serialPortUtils.setOnDataReceiveListener(new SerialPortUtils.OnDataReceiveListener() {
                @Override
                public void onDataReceive(byte[] buffer, int contentLenght, int cursor, int headerLength) throws UnsupportedEncodingException {
                    byte[] bytes = new byte[contentLenght];
                    System.arraycopy(buffer, cursor + headerLength, bytes, 0, contentLenght);
                    String data = new String(bytes, "utf8");
                    //CRC??????
                    if (EncapsulationFormat.judgeCRC(data)) {
                        String string = EncapsulationFormat.interceptDataBits(data); //???????????????
                        serialPortQueue.maxPut(string, QueueSize.MaxSize);
                    }
                }
            });
        }
    }

    class ConnectionVerificationTimer extends TimerTask {
        @Override
        public void run() {
            LogParameters.Running(0,
                    "TY-CPE1-V1.0????????????:+" + ++seq,
                    true
            );
            ConnectionVerificationPublishOne verificationPublishOne = new ConnectionVerificationPublishOne();
            verificationPublishOne.setSyn(1);
            verificationPublishOne.setSeq(seq); //????????????+1
            //??????
            String data = EncapsulationFormat.initializationEncapsulation
                    (InitCpeAnalysis.toJsonString(verificationPublishOne), 4);
            issueQueue.maxPut(data, QueueSize.MaxSize);
        }
    }

    class SerialPortQueueThread extends Thread {
        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            while (true) {
                if (!serialPortQueue.empty()) {
                    String data = serialPortQueue.get();
                    //todo ??????????????????????????????????????????????????????????????????,?????????????????????????????????????????????
                    //1.??????????????????
                    String dataType = EncapsulationFormat.interceptMessageType(data);
                    //2.???????????????
                    String dataBuffer = EncapsulationFormat.interceptData(data);
                    //3.??????????????????
                    Integer type = EncapsulationFormat.messageTypeJudgment(dataType);
                    switch (type) {
                        case 0: {
                            //todo ???????????????,CPE2????????????CPE1????????????????????????
                            Log.d("???????????????", dataBuffer);
                            InitializationConfiguration initializationConfiguration =
                                    InitCpeAnalysis.toJson(dataBuffer, InitializationConfiguration.class);
                            if (initializationConfiguration.getMsg().equals("OK")) {
                                SharedPreferences pre = getSharedPreferences("data", MODE_PRIVATE);
                                SharedPreferences.Editor editor = pre.edit();
                                LogParameters.Running(0,
                                        "cpe1uid:+" + SignalIntensity.getSERIAL() + "\n" +
                                                "cpe2uid:" + initializationConfiguration.getUid(),
                                        true
                                );
                                editor.putString("cpe1uid", SignalIntensity.getSERIAL());
                                editor.putString("cpe2uid", initializationConfiguration.getUid());
                                editor.putBoolean("judge", true);
                                editor.apply(); //??????????????????
                                //todo ??????????????????
                                /**
                                 * ??????????????????
                                 */
                                bindService = new BindService
                                        (MainActivity.this, new MqttClientServiceConnection());
                                //??????,????????????onStartCommand??????
                                bindService.serviceOnBind();
                            } else {
                                LogParameters.Running(0,
                                        "???????????????:??????,????????????????????????",
                                        false
                                );
                            }
                            break;
                        }

                        case 1: {
                            //todo ??????MQTT???????????????
                            cloudDataQueue.maxPut(dataBuffer, QueueSize.MaxSize);
                            break;
                        }

                        case 2: {
                            //todo ????????????????????????????????????
                            Log.d("??????", dataBuffer);
                            RunningLog runningLog =
                                    InitCpeAnalysis.toJson(dataBuffer, RunningLog.class);
                            RunningLog runningLog1 = new RunningLog();
                            runningLog1.setCpeType(runningLog.getCpeType());
                            runningLog1.setData(runningLog.getData());
                            runningLog1.setSimId(runningLog.getSimId());
                            runningLog1.setStatus(runningLog.getStatus());
                            runningLog1.setTimestamp(runningLog.getTimestamp());
                            runningLog1.setType(runningLog.getType());
                            runningLog1.setUid(runningLog.getUid());
                            runningLog1.save();
                            Log.e("????????????", String.valueOf(runningLog));
                            break;
                        }
                        case 3: {
                            //todo ??????????????????????????? ??? ????????????CPE2???UID????????????
                            heartbeatQueue.maxPut(dataBuffer, QueueSize.MaxSize);
                            break;
                        }
                        case 4: {
                            //todo ??????????????????
                            Log.d("??????????????????", dataBuffer);
                            ConnectionVerificationReceive connectionVerificationReceive =
                                    InitCpeAnalysis.toJson(dataBuffer, ConnectionVerificationReceive.class);
                            Integer SYN = connectionVerificationReceive.getSyn(); //????????????
                            Integer ACK = connectionVerificationReceive.getAck(); //??????????????????
                            Integer cpe2_seq = connectionVerificationReceive.getSeqq(); //????????????????????????
                            Integer cpe2_ack = connectionVerificationReceive.getAckk(); //??????????????????+1
                            Log.d("TY-CPE1-V1.0??????", "??????seq:" + cpe2_seq);
                            Log.d("TY-CPE1-V1.0??????", "??????ack:" + cpe2_ack);
                            Log.d("TY-CPE1-V1.0??????seq", "" + seq);
                            LogParameters.Running(0,
                                    "TY-CPE1-V1.0??????,??????seq:" + cpe2_seq + "\n" +
                                            "TY-CPE1-V1.0??????,??????ack:" + cpe2_ack + "\n" +
                                            "TY-CPE1-V1.0??????seq",
                                    true
                            );
                            if (SYN == 1 && ACK == 1 && cpe2_ack == seq + 1) {
                                Log.d("TY-CPE1-V1.0", "??????TY-CPE1-V1.0?????????TY-CPE2-V1.0??????");
                                LogParameters.Running(3,
                                        "TY-CPE1-V1.0:??????TY-CPE1-V1.0?????????TY-CPE2-V1.0??????",
                                        true
                                );
                                timerConnection.cancel();
                                timerConnection = null;
                                ConnectionVerificationPublishTwo connectionVerificationPublishTwo = new ConnectionVerificationPublishTwo();
                                connectionVerificationPublishTwo.setAck(1);
                                connectionVerificationPublishTwo.setSeqq(cpe2_ack);
                                connectionVerificationPublishTwo.setAckk(cpe2_seq + 1);
                                issueQueue.maxPut(EncapsulationFormat.initializationEncapsulation
                                                (InitCpeAnalysis.toJsonString(connectionVerificationPublishTwo), 4)
                                        , QueueSize.MaxSize);
                            } else {
                                LogParameters.Running(0,
                                        "SYN???ACK?????????",
                                        false
                                );
                            }
                            break;
                        }
                        default: {
                            Log.d("??????????????????", dataBuffer);
                            LogParameters.Running(0,
                                    "??????????????????",
                                    false
                            );
                            break;
                        }

                    }
                }
            }
        }
    }

    class CloudDataQueueThread extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                if (!cloudDataQueue.empty() && allowToSend) {
                    String cloudData = cloudDataQueue.get();
                    MqttClientService.send(SgitTopic.CPE_C2S, cloudData);
                    Log.d("???????????????", cloudData);
                }
            }
        }
    }

    class IssueQueueThread extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                //????????????????????????????????????????????????
                if (!issueQueue.empty() && serialPort != null) {
                    String str = issueQueue.get();

                    if (str.length() > QueueSize.MaxSize) {
                        LogParameters.Running(6,
                                "??????????????????,??????",
                                false
                        );
                    } else {
                        serialPortUtils.sendSerialPort(str);
                        Log.d("????????????", str);
                    }

                }
            }
        }
    }

    class HeartbeatLogThread extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                switch (IntenetUtil.getNetworkState(MainActivity.this)) {
                    case 0: {
                        //?????????
                        break;
                    }
                    default: {
                        //??????
                        if (!heartbeatQueue.empty()) {
                            String data = heartbeatQueue.get();
                            Log.d("????????????", data);

                            //??????RequestBody
                            RequestBody requestBody = RequestBody.create(JSON, data);
                            //?????? ??????
                            HttpClient.httpPostJSONJava(heartbeatLogUrl, requestBody, null);
                        }
                        break;
                    }
                }

            }
        }
    }

    class InitDataBaseTimer extends TimerTask {
        @Override
        public void run() {
            InitDataBase.delete();
        }
    }

    class RunningLogTimer extends TimerTask {
        @SneakyThrows
        @Override
        public void run() {

            switch (IntenetUtil.getNetworkState(MainActivity.this)) {
                case 0: {
                    break;
                }
                default: {
                    //??????
                    List<RunningLog> runningLogs = LitePal.findAll(RunningLog.class);
                    if (runningLogs != null) {
                        for (int i = 0; i < runningLogs.size(); i++) {
                            Log.d("????????????", InitCpeAnalysis.toJsonString(runningLogs.get(i)));
                            //??????RequestBody??????
                            RequestBody requestBody =
                                    RequestBody.create
                                            (JSON, InitCpeAnalysis.toJsonString(runningLogs.get(i)));
                            HttpClient.httpPostJSONJava(runningLogUrl, requestBody, null);
                            synchronized (this) {
                                list_runlog.add(runningLogs.get(i).getData());
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (runningLogAdapter != null)
                                            runningLogAdapter.notifyDataSetChanged();
                                    }
                                });
                            }
                            //????????????????????????????????????
                            runningLogs.get(i).delete();
                        }
                    }
                    break;
                }
            }
        }
    }

    class HeartbeatLogTimer extends TimerTask {
        @SneakyThrows
        @Override
        public void run() {

            HeartbeatLog heartbeatLog = LogParameters.Heart
                    (0, true, MainActivity.this);

            synchronized (this) {
                list_heartlog.add(heartbeatLog);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (heartLogAdapter != null)
                        heartLogAdapter.notifyDataSetChanged();
                }
            });

            //????????????
            heartbeatQueue.maxPut(InitCpeAnalysis.toJsonString(heartbeatLog), QueueSize.MaxSizeLength);
        }
    }

}

