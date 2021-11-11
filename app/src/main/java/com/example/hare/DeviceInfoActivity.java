package com.example.hare;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hare.Module.Adapter.ExpandableListAdapter;
import com.example.hare.Module.Enitiy.ScannedData;
import com.example.hare.Module.Enitiy.ServiceInfo;
import com.example.hare.Module.Service.BluetoothLeService;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.CharucoBoard;
import org.opencv.aruco.Dictionary;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

public class DeviceInfoActivity extends AppCompatActivity implements ExpandableListAdapter.OnChildClick ,CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String     TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;
    private TextView tvAddress,tvStatus,tvRespond;
    private ExpandableListAdapter expandableListAdapter;
    private boolean isLedOn = false;

    private Size SIZE = new Size();

    private Mat mRgba, mRgb;
    private Mat cameraMatrix , distCoeffs , rvec , tvec;
    private Dictionary dict;
    private List<Mat> detectedMarkers;
    private Mat       ids;
    private Scalar borderColor;
    private int                 counter = 0;
    private CharucoBoard board;
    private ArrayList<Mat> charucoCorners, charucoIds, rvecs, tvecs;
    private Point center;
    private  int width_scr, height_scr;
    private double[] cen_point = new double[0];
    private int distance_X, distance_Y;

    boolean isLogo = false,ready = false;

    int cen_width ,cen_height;
    int pointTL, pointDR , time=0;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    dict = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250);
                    board = CharucoBoard.create(5, 7, (float) 0.04, (float) 0.02, dict);
                    charucoCorners = new ArrayList<>();
                    charucoIds = new ArrayList<>();
                    borderColor = new Scalar(0,255,0);
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public DeviceInfoActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_device_control);

        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
        initBLE();
        initUI();

        mOpenCvCameraView = findViewById(R.id.surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }


    //get screen size
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        width_scr = mOpenCvCameraView.getWidth();
        height_scr = mOpenCvCameraView.getHeight();
        Log.e("WIDTH",""+mOpenCvCameraView.getWidth());
        Log.e("HEIGHT",""+mOpenCvCameraView.getHeight());
        //get the center point of width and height( x , y)
        cen_width = (int) (width_scr / 3.2);
        cen_height = height_scr / 2;
        //use for adjust width point (it`s different between device )
        //cen_width -= 240;
    }

    public void onCameraViewStarted(int width, int height) {
        //get opencv color mode
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgb = new Mat(height, width, CvType.CV_8UC3);
        SIZE = new Size(width, height);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mRgb.release();
        SIZE = null;
    }

    public void onCameraCalibrate(View view) {
        if (counter < 10) {
            ArrayList<Mat> aux = new ArrayList<>();
            Mat auxIds = new Mat();
            Aruco.detectMarkers(mRgb, dict, aux, auxIds);
            charucoCorners.addAll(aux);
            charucoIds.add(auxIds);
            counter++;
            //Log.d(TAG, "檢測到校準圖像 " + aux.size());
            //Log.d(TAG, "校準圖像: " + counter + "/10");
        } else {
            counter = 0;
            Aruco.calibrateCameraCharuco(charucoCorners, charucoIds, board, SIZE, cameraMatrix, distCoeffs, rvecs, tvecs);
        }
    }

    public Mat detectMarkers() {

        detectedMarkers = new ArrayList<>();
        ids = new Mat();
        center = new Point(0,0);
        isLogo = false;
        //detectMarker(image want to detect , mark type , input , mark id)
        Aruco.detectMarkers(mRgb, dict, detectedMarkers, ids);


        //get the 4 corner of detected mark
        //Mat corners = Converters.vector_Mat_to_Mat(detectedMarkers);

        for (Mat mat : detectedMarkers) {
            System.out.println(mat.dump());
            List<Point> cornerPoints = new ArrayList<>();
            for (int row = 0; row < mat.height(); row++) {
                for (int col = 0; col < mat.width(); col++) {
                    final Point point = new Point(mat.get(row, col));
                    cornerPoints.add(point);
                    Log.d(TAG, "校準圖像: " + point);

                }
            }
            //get top left and top right point
            getCornerPoint(cornerPoints);
            // get the screen center
            // get the detected tag`s center point
            cen_point = getCenter(cornerPoints);
            Log.d(TAG, "center point: " + cen_point);

            //取得x與y與中心點的距離
            distance_X = (int) ( cen_width - cen_point[0]);
            distance_Y = (int) ( cen_height - cen_point[1]);

            Log.d(TAG, "final pointX: " + distance_X);
            Log.d(TAG, "final pointY: " + distance_Y);

            //draw centqer of detected mark
            Imgproc.circle(mRgb, new Point(getCenter(cornerPoints)), 10, new Scalar(0, 0, 255), -1);
            //draw the line between center point and detect mark`s center point
            Imgproc.line(mRgb, new Point(getCenter(cornerPoints)), new Point(cen_width,cen_height), new Scalar(0, 255, 0));
            //maybe can control car using distance between center point and detect mark`s center point
            bleCon();
            //String sendData = "SRV1500150015001500#";
            //mBluetoothLeService.send(sendData.getBytes());
        }


        if (!isLogo && ready){
            String sendData;
            if(time <= 1)sendData = "SRV1500150015001500#";
            sendData = "SRV1500145015001500#";
            time ++;
        }

        if(time >= 8) {
            String sendData = "SRV1500150015001500#";
            mBluetoothLeService.send(sendData.getBytes());
            time = 0;
        }

        Aruco.drawDetectedMarkers(mRgb, detectedMarkers, ids, borderColor);

        return mRgb;
    }

    private void bleCon() {
        isLogo = true;
        ready = true;
        String channel1 ="1500";
        String channel2 ="1500";

        channel1 = Channel1(distance_X);
        channel2 = Channel2((int) cen_point[1]);

        String sendData = "SRV"+channel1+channel2+"15001500#";
        mBluetoothLeService.send(sendData.getBytes());

    }

    private String Channel2(int y ) {
        //go or reversing


        String send;
        int tmpUD = 0;

        if(y<50&&isLogo==false) {
            tmpUD = 1500;
        }
        else {
            tmpUD = (int) (1500+y*0.5);
        }

        if(tmpUD>=1580)tmpUD=1580;
        else if(tmpUD<=1500)tmpUD=1500;

        //int totalTD_distance = topleft + (height_scr-downright);
        /*if(totalTD_distance >= 300 ) {
            tmpUD = (int) (pixel2value2*totalTD_distance);
            if(tmpUD>=2000)tmpUD=2000;
            else if(tmpUD<=1000)tmpUD=1000;
        }
        */

        send = Integer.toString(tmpUD);

        return send;
    }


    private String Channel1(int x) {
        //bluetooth here
        //logic here
        //left----------------mid-------------------right
        //2000----------------1500------------------1000

        float pixel2value,pixel2value2;
        int tmpLR=0,tmpUD =0;

        //left right moving
        pixel2value= 2000/width_scr;
        x = (int) (x*pixel2value);
        //tmpLR = x + cen_width;
        System.out.println("pixel2Val"  + pixel2value);
        System.out.println("x: " + x);
        System.out.println("tmpLR" + tmpLR);
        //if cen_width is 1080/2=540 and 1500/540=2.78 ,it`s mean 1px need send 2.78 value
        tmpLR = (int) (x*1.5);
        tmpLR += 1500;
        if(tmpLR>=2000)tmpLR=2000;

        else if(tmpLR<=1000)tmpLR=1000;


        String send = Integer.toString(tmpLR);

        return send;




    }

    public double[] getCenter(List<Point> points) {
        final MatOfPoint points_ = new MatOfPoint();
        points_.fromList(points);
        return getCenter(points_);
    }

    public double[] getCenter(MatOfPoint points) {
        Moments moments = Imgproc.moments(points);
        double[] center = { moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00() };
        return center;
    }

    public void getCornerPoint(List<Point> points){
        final MatOfPoint points_ = new MatOfPoint();
        points_.fromList(points);
        //this is point top left
        double[] getpoint1 = points_.get(0,0);
        Imgproc.circle(mRgb, new Point(getpoint1), 10, new Scalar(255, 255, 0), -1);

        pointTL = (int) getpoint1[1];
        System.out.println("topleft "  + pointTL);
        //this is point down right
        double[] getpoint2 = points_.get(2,0);
        Imgproc.circle(mRgb, new Point(getpoint2), 10, new Scalar(255, 255, 0), -1);
        pointDR = (int) getpoint2[1];
        System.out.println("downright "  + pointDR);
        //draw a circle between top and down
        Imgproc.circle(mRgb, new Point(getpoint1[0],0), 10, new Scalar(255, 255, 0), -1);
        Imgproc.circle(mRgb, new Point(getpoint2[0],height_scr-50), 10, new Scalar(255, 255, 0), -1);
        Imgproc.line(mRgb, new Point(getpoint1), new Point(getpoint1[0],0), new Scalar(255, 0, 255));
        Imgproc.line(mRgb, new Point(getpoint2), new Point(getpoint2[0],height_scr-50), new Scalar(255, 0, 255));

    }




    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {


        Imgproc.cvtColor(inputFrame.rgba(), mRgb, Imgproc.COLOR_RGBA2RGB);

        Log.d(TAG, "center_H: " + cen_height);
        Log.d(TAG, "center_W: " + cen_width);
        // draw circle at center point
        Imgproc.circle(mRgb, new Point(cen_width,cen_height), 10, new Scalar(0, 0, 255), -1);
        return detectMarkers();
    }

    /**初始化藍芽*/
    private void initBLE(){
        /**綁定Service
         * @see BluetoothLeService*/
        Intent bleService = new Intent(this, BluetoothLeService.class);
        bindService(bleService,mServiceConnection,BIND_AUTO_CREATE);
        /**設置廣播*/
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);//連接一個GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);//從GATT服務中斷開連接
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);//查找GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);//從服務中接受(收)數據

        registerReceiver(mGattUpdateReceiver, intentFilter);
        if (mBluetoothLeService != null) mBluetoothLeService.connect(selectedDevice.getAddress());
    }
    /**初始化UI*/
    private void initUI(){
        expandableListAdapter = new ExpandableListAdapter();
        expandableListAdapter.onChildClick = this::onChildClick;
        //ExpandableListView expandableListView = findViewById(R.id.gatt_services_list);
        //expandableListView.setAdapter(expandableListAdapter);
        tvAddress = findViewById(R.id.device_address);
        tvStatus = findViewById(R.id.connection_state);
        tvRespond = findViewById(R.id.data_value);
        tvAddress.setText(selectedDevice.getAddress());
        tvStatus.setText("未連線");
        tvRespond.setText("---");
    }
    /**藍芽已連接/已斷線資訊回傳*/
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            mBluetoothLeService.connect(selectedDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService.disconnect();
        }
    };
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            /**如果有連接*/
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "藍芽已連線");
                tvStatus.setText("已連線");

            }
            /**如果沒有連接*/
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "藍芽已斷開");

            }
            /**找到GATT服務*/
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "已搜尋到GATT服務");
                List<BluetoothGattService> gattList =  mBluetoothLeService.getSupportedGattServices();
                displayGattAtLogCat(gattList);
                expandableListAdapter.setServiceInfo(gattList);
            }
            /**接收來自藍芽傳回的資料*/
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG, "接收到藍芽資訊");
                byte[] getByteData = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                StringBuilder stringBuilder = new StringBuilder(getByteData.length);
                for (byte byteChar : getByteData)
                    stringBuilder.append(String.format("%02X ", byteChar));
                String stringData = new String(getByteData);
                Log.d(TAG, "String: "+stringData+"\n"
                        +"byte[]: "+BluetoothLeService.byteArrayToHexStr(getByteData));
                tvRespond.setText("String: "+stringData+"\n"
                        +"byte[]: "+BluetoothLeService.byteArrayToHexStr(getByteData));
                isLedOn = BluetoothLeService.byteArrayToHexStr(getByteData).equals("486173206F6E");


            }
        }
    };//onReceive
    /**將藍芽所有資訊顯示在Logcat*/
    private void displayGattAtLogCat(List<BluetoothGattService> gattList){
        for (BluetoothGattService service : gattList){
            Log.d(TAG, "Service: "+service.getUuid().toString());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()){
                Log.d(TAG, "\tCharacteristic: "+characteristic.getUuid().toString()+" ,Properties: "+
                        mBluetoothLeService.getPropertiesTagArray(characteristic.getProperties()));
                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()){
                    Log.d(TAG, "\t\tDescriptor: "+descriptor.getUuid().toString());
                }
            }
        }
    }
    /**關閉藍芽*/
    private void closeBluetooth() {
        if (mBluetoothLeService == null) return;
        mBluetoothLeService.disconnect();
        unbindService(mServiceConnection);
        unregisterReceiver(mGattUpdateReceiver);

    }

    @Override
    protected void onStop() {
        super.onStop();
        closeBluetooth();
    }

    /**點擊物件，即寫資訊給藍芽(或直接讀藍芽裝置資訊)*/
    @Override
    public void onChildClick(ServiceInfo.CharacteristicInfo info) {
        String sendData = "SRV2000150015001500#";
        mBluetoothLeService.send(sendData.getBytes());
    }
}
