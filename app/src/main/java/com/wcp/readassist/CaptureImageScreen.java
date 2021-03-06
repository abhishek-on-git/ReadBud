package com.wcp.readassist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.wcp.readassist.utils.ReadAssistUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CaptureImageScreen extends AppCompatActivity implements ReadAssistUtils.TaskStatusCallback{

    private static final String TAG = "CaptureImageScreen";
    private View mCloseButton;
    private ImageView mTakePictureButton;
    private ImageView mImagePreview;
    private Button mCreateEBookButton;
    private Button mCreateEBookButtonFinal;
    private TextureView mCameraSurface;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraStateCallback;
    private CameraManager mCameraManager;
    private String mCameraId;
    private Size mPreviewSize;
    private CaptureRequest mCaptureRequest;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mCaptureSessionCallback;
    private View mImagePreviewContainer;
    private Button mAddPageButton;
    private Button mRetryButton;
    private CheckBox mConfirmationCheckBox;
    private RecyclerView mImageRecyclerView;
    private ImageAdapter mImageAdapter;
    private RecyclerView.LayoutManager mRecyclerLayoutManager;
    private TextView mPageNumberTextView;
    private View mEbookNameView;
    private EditText mEbookNameField;
    private TextView mEmptyNameErrorView;
    private ProgressDialog mProgressDialog;

    private Context mContext;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private File mImageFile;
    private ImageReader mImageReader;
    private ImageReader.OnImageAvailableListener mImageAvailabilityListener;

    private Bitmap mCurrentCapture;
    private List<Bitmap> mCapturedList;
    private InputMethodManager mImm;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int CAMERA_REQUEST = 1888;
    private static final String CAMERA_BACKGROUND_THREAD_NAME = "camera_background_thread";
    private static final int CAMERA_STATE_PREVIEW = 0;
    private static final int CAMERA_STATE_WAIT_LOCKED = 1;
    private static final int MAX_IMAGE_WIDTH = 1080;
    private static final int PERMISSIONS_REQUEST_CODE = 1000;
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private int mState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_image_screen);
        inititialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if(mCameraSurface != null && !mCameraSurface.isAvailable()) {
            mCameraSurface.setSurfaceTextureListener(mSurfaceTextureListener);
        } else if (mCameraSurface != null) {
            setUpCamera(mCameraSurface.getWidth(), mCameraSurface.getHeight());
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
        endBackgroundThread();
    }

    private void inititialize() {
        ensurePermissions();
        mContext = this;
        ReadAssistUtils.registerTaskStatusCalback(this);
        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        mCameraSurface = (TextureView) findViewById(R.id.camera_surface);
        mTakePictureButton = (ImageView) findViewById(R.id.take_picture_button);
        mImagePreview = (ImageView) findViewById(R.id.image_preview);
        mCreateEBookButton = (Button) findViewById(R.id.create_ebook_button);
        //mCreateEBookButton.setEnabled(false);
        mCreateEBookButtonFinal = (Button) findViewById(R.id.create_ebook_with_name);
        mCloseButton = (ImageView) findViewById(R.id.close_buton);
        mImagePreviewContainer = findViewById(R.id.image_preview_container);
        mAddPageButton = (Button) findViewById(R.id.add_to_list_button);
        mRetryButton = (Button) findViewById(R.id.retry_button);
        mPageNumberTextView = (TextView) findViewById(R.id.page_number_text);
        mImageRecyclerView = (RecyclerView) findViewById(R.id.image_recycler_view);
        mEbookNameView = findViewById(R.id.ebook_details_form);
        mEbookNameField = (EditText) findViewById(R.id.ebook_name_field);
        mEmptyNameErrorView = (TextView) findViewById(R.id.error_empty_name);
        mProgressDialog = new ProgressDialog(this);

        mImm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mImageAdapter = new ImageAdapter();
        mRecyclerLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mImageRecyclerView.setAdapter(mImageAdapter);
        mImageRecyclerView.setLayoutManager(mRecyclerLayoutManager);

        mTakePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
                //capturePhoto(null);
            }
        });
        mTakePictureButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mTakePictureButton.setAlpha(1f);
                        mTakePictureButton.setScaleX(1f);
                        mTakePictureButton.setScaleY(1f);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mTakePictureButton.setAlpha(0.8f);
                        mTakePictureButton.setScaleX(0.9f);
                        mTakePictureButton.setScaleY(0.9f);

                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                }
                return false;
            }
        });
        mCreateEBookButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mCreateEBookButton.setAlpha(1f);
                        mCreateEBookButton.setScaleX(1f);
                        mCreateEBookButton.setScaleY(1f);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mCreateEBookButton.setAlpha(0.9f);
                        mCreateEBookButton.setScaleX(0.98f);
                        mCreateEBookButton.setScaleY(0.98f);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                }
                return false;
            }
        });
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mAddPageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int size = mImageAdapter.addToImageList(mCurrentCapture);
                mImageRecyclerView.smoothScrollToPosition(size-1);
                previewContainerAppearance(false);
                Resources res = getResources();
                String msg = size > 1 ? String.format(res.getString(R.string.n_pages_captured_text), size) :
                        String.format(res.getString(R.string.one_page_captured_text), 1);
                mPageNumberTextView.setText(msg);
            }
        });

        mRetryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previewContainerAppearance(false);
            }
        });

        mCreateEBookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEBook();
                //createEBookWithName();
            }
        });

        mCreateEBookButtonFinal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEBook();
            }
        });

        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setUpCamera(width, height);
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };

        mCameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                mCameraDevice = camera;
                startCameraPreviewSession();
                //Toast.makeText(mContext, "Camera opened", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                camera.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                camera.close();
                mCameraDevice = null;
                Log.e(ReadAssistUtils.TAG, "Camera onError reason : "+error);
            }
        };

        mCaptureSessionCallback = new CameraCaptureSession.CaptureCallback() {
            private void processCaptureResult(CaptureResult result) {
                switch (mState) {
                    case CAMERA_STATE_PREVIEW :
                        break;
                    case CAMERA_STATE_WAIT_LOCKED :
                        Integer autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE);
                        Log.d(ReadAssistUtils.TAG, "autoFocusState = "+autoFocusState);
                        if(autoFocusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                            //unLockCameraFocus();
                            //Toast.makeText(mContext, "Focus locked", Toast.LENGTH_SHORT).show();
                            captureStill();
                        }
                        break;
                }
            }
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                processCaptureResult(result);
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Toast.makeText(mContext, "Focus lock unsuccessful", Toast.LENGTH_SHORT).show();
            }
        };

        mImageAvailabilityListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(final ImageReader reader) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getCapturedBitmap(reader.acquireNextImage());
                    }
                });
            }
        };
    }

    private Handler getCaptureHadler() {
        return mHandler;
    }

    private void createEBookWithName() {
        mEbookNameView.setVisibility(View.VISIBLE);
        mCreateEBookButton.setVisibility(View.INVISIBLE);
    }

    private void createEBook() {
//        if(TextUtils.isEmpty(mEbookNameField.getText())) {
//            mEmptyNameErrorView.setVisibility(View.VISIBLE);
//            Animation vibrate = AnimationUtils.loadAnimation(this, R.anim.error_shake);
//            mEmptyNameErrorView.startAnimation(vibrate);
//            return;
//        }
//        String name = mEbookNameField.getText().toString();
//        mEmptyNameErrorView.setVisibility(View.INVISIBLE);
//        mImm.hideSoftInputFromWindow(mEbookNameView.getWindowToken(), 0);
//        mEbookNameView.setVisibility(View.GONE);
//        mCreateEBookButton.setVisibility(View.VISIBLE);
        mCapturedList = mImageAdapter.getImageList();
        if(mCapturedList.size() == 0) {
            Toast.makeText(this, "Please take a picture first", Toast.LENGTH_SHORT).show();
            return;
        }
        if(mProgressDialog != null) {
            mProgressDialog.setMessage(getString(R.string.extracting_text_message));
            mProgressDialog.show();
        }
        ReadAssistUtils.extractTextFromImageList(this, mCapturedList, mHandler, "", null);
    }

    private void setUpCamera(int width, int height) {
        if (mCameraManager != null) {
            try {
                for (String cameraId : mCameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_BACK) {
                        Log.d(ReadAssistUtils.TAG, "lens facing back");

                    }
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    Size largestImageSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                        @Override
                        public int compare(Size o1, Size o2) {
                            return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                        }
                    });

                    mImageReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(),
                            ImageFormat.JPEG, 1);
                    mImageReader.setOnImageAvailableListener(mImageAvailabilityListener, mHandler);

                    mPreviewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                    mCameraId = cameraId;
                    Log.d(ReadAssistUtils.TAG, "cameraid = "+ mCameraId);
                    //TODO : check how to avoid the wide angle lens
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openCamera() {
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                //TODO request permission here again.
                return;
            }
            mCameraManager.openCamera(mCameraId, mCameraStateCallback, mHandler);
        } catch(Exception e) {

        }
    }

    private void closeCamera() {
        if(mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if(mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void capturePhoto(View view) {
        try {
            mImageFile = createImageFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        lockCameraFocus();
    }

    private int getJPEGRotation(int rotation) {
        try {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(mCameraId);
            int sensorOrientation =  c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int surfaceRotation = ORIENTATIONS.get(rotation);
            int jpegOrientation = (surfaceRotation + sensorOrientation + 180) % 360;
            Log.d(ReadAssistUtils.TAG, "sensor = "+sensorOrientation+" surface = "+surfaceRotation+" jpeg = "+jpegOrientation+"rotation = "+rotation);
            return jpegOrientation;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    private void captureStill() {
        try {
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //Toast.makeText(mContext, "Image captured", Toast.LENGTH_SHORT).show();
                    unLockCameraFocus();
                }
            };
            mCameraCaptureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File createImageFile() {
        return null;
    }

    private void lockCameraFocus() {
        try {
            mState = CAMERA_STATE_WAIT_LOCKED;
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureSessionCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unLockCameraFocus() {
        try {
            mState = CAMERA_STATE_PREVIEW;
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureSessionCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCameraPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = mCameraSurface.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if(mCameraDevice != null) {
                        try {
                            mCaptureRequest = mCaptureRequestBuilder.build();
                            mCameraCaptureSession = session;
                            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, mCaptureSessionCallback, mHandler);
                        } catch (Exception e) {

                        }
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(mContext, "Camera configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Size getPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for(Size sizeOption : mapSizes) {
            if(width > height) {
                if(sizeOption.getWidth() > width &&
                        sizeOption.getHeight() > height) {
                    collectorSizes.add(sizeOption);
                }
            } else {
                if(sizeOption.getWidth() > height &&
                        sizeOption.getHeight() > width){
                    collectorSizes.add(sizeOption);
                }
            }
            if(collectorSizes.size() > 0) {
                return Collections.min(collectorSizes, new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                    }
                });
            }
        }
        return mapSizes[0];
    }

    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread(ReadAssistUtils.CAMERA_BACKGROUND_THREAD_NAME);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    private void endBackgroundThread() {
        mHandlerThread.quitSafely();
        try{
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void ensurePermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        for(String permission : PERMISSIONS) {
            if(checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if(!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded.toArray(new String[permissionsNeeded.size()]), PERMISSIONS_REQUEST_CODE);
        }
    }
    //TODO Improve collective permission code. Add code for permission denial : https://medium.com/mindorks/multiple-runtime-permissions-in-android-without-any-third-party-libraries-53ccf7550d0
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSIONS_REQUEST_CODE) {
            for(int i = 0; i < grantResults.length; i++) {
                if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
           // mCapturedImagePreview.setImageBitmap(photo);
        }
    }

    private void previewContainerAppearance(boolean show) {
        mImagePreviewContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        mTakePictureButton.setVisibility(show ? View.GONE : View.VISIBLE);
        if(!show) {
            mImagePreview.setImageBitmap(null);
        }
    }

    private void setCurrentImage(Bitmap image) {
        mCurrentCapture = image;
        previewContainerAppearance(true);
        mImagePreview.setImageBitmap(mCurrentCapture);
    }

    private void captureImage() {
        if(mCameraSurface != null) {
            Bitmap bitmap = mCameraSurface.getBitmap();
            setCurrentImage(bitmap);
        }
    }

    private void getCapturedBitmap(Image image) {
        Log.d(ReadAssistUtils.TAG, "image width = "+image.getWidth()+" height = "+image.getHeight());
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byte[] imgByte = new byte[byteBuffer.remaining()];
        byteBuffer.get(imgByte);
        Bitmap bitmap, scaledBitmap;
        if(width > height) {
            int ratio = width/height;
            width = MAX_IMAGE_WIDTH;
            height = width/ratio;
            Matrix matrix = new Matrix();
            matrix.setRotate(90);
            bitmap = BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length, null);
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            mCurrentCapture = Bitmap.createBitmap(scaledBitmap, 0, 0, width, height, matrix, true);
        } else {
            bitmap = BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length, null);
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            mCurrentCapture = scaledBitmap;
        }
        setCurrentImage(mCurrentCapture);
        image.close();
    }

    @Override
    public void onTextRecognitionCompleted(String eBookName, List<DigitalPage> digitalPageList) {
        ReadAssistUtils.writeToFile(mContext, eBookName);
    }

    @Override
    public void onFileWritten(String eBookName) {
        if(mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        resetStates();
        Intent intent = new Intent(this, EbookActivity.class);
        intent.putExtra(ReadAssistUtils.BOOK_NAME, eBookName);
        intent.putExtra(ReadAssistUtils.SOURCE, 0);
        startActivity(intent);
    }

    @Override
    public void onFileRead(String eBookName, String[] text) {

    }

    @Override
    public void onTextExtractionComplete(String eBookName) {

    }

    @Override
    public void onPageExtractionComplete(int pageNumber) {

    }

    @Override
    public void onError(boolean showMessage, String fileName) {

    }

    @Override
    public void onDBcreated() {

    }

    private void resetStates() {
        mCurrentCapture = null;
        mImageAdapter.clearImageList();
        mCapturedList.clear();
        mPageNumberTextView.setText("");
        mImageAdapter.notifyDataSetChanged();
    }

    //    protected class ImageSaver implements Runnable {
//        private final Image mImage;
//
//        private ImageSaver(Image image) {
//            mImage = image;
//        }
//
//        @Override
//        public void run() {
//            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
//            byte[] imgByte = new byte[byteBuffer.remaining()];
//            byteBuffer.get(imgByte);
//            mCurrentCapture = BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length, null);
//            setCurrentImage(mCurrentCapture);
//            mImage.close();
//        }
//    }
}