package com.example.hare;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.example.hare.Module.Enitiy.ScannedData;
import com.example.hare.Module.Service.BluetoothLeService;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.CharucoBoard;
import org.opencv.aruco.Dictionary;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;



public class MainActivity extends Activity implements CvCameraViewListener2 {
    private static final String     TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private Size                    SIZE = new Size();

    private Mat                 mRgba, mRgb;
    private Dictionary          dict;
    private List<Mat>           detectedMarkers;
    private Mat                 ids, cameraMatrix, distCoeffs;
    private Scalar              borderColor;
    private int                 counter = 0;
    private CharucoBoard        board;
    private ArrayList<Mat>      charucoCorners, charucoIds, rvecs, tvecs;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
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

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);


        Button faceDetect = findViewById(R.id.button);
        faceDetect.setOnClickListener(onClickListener);

        Button ScanBluetooth = findViewById(R.id.Bluetooth);
        ScanBluetooth.setOnClickListener(onClickListener);

        mOpenCvCameraView = findViewById(R.id.surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 1);
        }

    }

    public View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.button:
                        startActivity(new Intent(MainActivity.this, aruco_detect.class));
                    break;
                case R.id.Bluetooth:
                        startActivity(new Intent(MainActivity.this, ScanBluetooth.class));
                    break;
           /* case R.id.tag_dectect:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA}, 1);
                } else {
                    startActivity(new Intent(this, DetectActivity.class));
                }
            */
                default:
            }
        }
    };

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

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgb = new Mat(height, width, CvType.CV_8UC3);
        SIZE = new Size(width, height);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mRgb.release();
        SIZE = null;
    }


    public Mat detectMarkers() {
        detectedMarkers = new ArrayList<>();
        ids = new Mat();
        Aruco.detectMarkers(mRgb, dict, detectedMarkers, ids);
        Aruco.drawDetectedMarkers(mRgb, detectedMarkers, ids, borderColor);
        return mRgb;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Imgproc.cvtColor(inputFrame.rgba(), mRgb, Imgproc.COLOR_RGBA2RGB);
        return detectMarkers();
    }
}
