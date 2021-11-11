package com.example.hare;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

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



public class aruco_detect<point> extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String     TAG = "MainActivity";
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

    int cen_width ,cen_height;

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

    public aruco_detect() {
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

        setContentView(R.layout.detect);

        //mark_loc = findViewById(R.id.textView);


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
        cen_width = width_scr / 2;
        cen_height = height_scr / 2;
        //use for adjust width point (it`s different between device )
        cen_width -= 260;
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
            // get the screen center
            // get the detected tag`s center point
            cen_point = getCenter(cornerPoints);
            Log.d(TAG, "center point: " + cen_point);

            //取得x與y與中心點的距離
            distance_X = (int) (cen_point[0] - cen_width);
            distance_Y = (int) (cen_point[1] - cen_height);

            Log.d(TAG, "final pointX: " + distance_X);
            Log.d(TAG, "final pointY: " + distance_Y);

            //draw center of detected mark
             Imgproc.circle(mRgb, new Point(getCenter(cornerPoints)), 10, new Scalar(0, 0, 255), -1);
             //draw the line between center point and detect mark`s center point
             Imgproc.line(mRgb, new Point(getCenter(cornerPoints)), new Point(cen_width,cen_height), new Scalar(0, 255, 0));
             //maybe can control car using distance between center point and detect mark`s center point
             BlueControl(distance_X, distance_Y);
        }

        Aruco.drawDetectedMarkers(mRgb, detectedMarkers, ids, borderColor);

        return mRgb;
    }

    private void BlueControl(int x, int y) {
        //bluetooth here

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



    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Imgproc.cvtColor(inputFrame.rgba(), mRgb, Imgproc.COLOR_RGBA2RGB);

        Log.d(TAG, "center_H: " + cen_height);
        Log.d(TAG, "center_W: " + cen_width);
        // draw circle at center point
        Imgproc.circle(mRgb, new Point(cen_width,cen_height), 10, new Scalar(0, 0, 255), -1);
        return detectMarkers();
    }
}
