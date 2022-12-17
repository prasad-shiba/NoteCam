package com.demo.notecam;

import android.Manifest;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.LifecycleCameraController;
import androidx.core.content.ContextCompat;

import com.demo.notecam.databinding.ActivityCameraBinding;
import com.demo.notecam.databinding.MetaDataBinding;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

  private static final String TAG = "CameraActivity";
  private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
  private final LocationUtil locationUtil = new LocationUtil(this);
  private ActivityCameraBinding binding;
  private MetaDataBinding metaDataBinding;
  private Location location;
  private final LocationUtil.LocationResult locationResult =
      new LocationUtil.LocationResult() {
        @Override
        public void gotLocation(Location location) {
          // Got the location!
          CameraActivity.this.location = location;
          if (metaDataBinding != null && location != null) {
            metaDataBinding.longitudeTv.setVisibility(View.VISIBLE);
            metaDataBinding.latitudeTv.setVisibility(View.VISIBLE);
            metaDataBinding.accuracyTv.setVisibility(View.VISIBLE);
            metaDataBinding.accuracyTv.setText(
                getString(R.string.accuracy) + " " + location.getAccuracy() + " m");
            metaDataBinding.latitudeTv.setText(
                getString(R.string.latitude) + " " + location.getLatitude());
            metaDataBinding.longitudeTv.setText(
                getString(R.string.longitude) + " " + location.getLongitude());
          }
        }
      };
  private SoundPool soundPool = new SoundPool.Builder().build();
  private int soundId;
  private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
  private ActivityResultLauncher<String[]> resultLauncher;
  private ExecutorService cameraExecutor;
  private LifecycleCameraController cameraController;
  private Handler handler;
  private final Runnable timeUpdater =
      new Runnable() {
        @Override
        public void run() {
          if (handler != null && metaDataBinding != null) {
            metaDataBinding.timeTv.setText(getString(R.string.time) + " " + Util.getCurrentTime());
            handler.postDelayed(this, 1000);
          }
        }
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setFullScreen();
    binding = ActivityCameraBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    // Request camera permissions
    init();
    if (Util.isPermissionGranted(this, Manifest.permission.CAMERA)) {
      startCamera();
    } else {
      requestPermission(Manifest.permission.CAMERA);
    }
    locationUtil.getLocation(locationResult);
    initViews();
    init();
    // Set up the listeners for take photo capture and other buttons
    onClickEvents();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    cameraExecutor.shutdown();
    soundPool.release();
    soundPool = null;
  }

  private Dialog getDialog(String permission) {
    return Util.createPermissionDialog(
        this,
        permission,
        new OnPermissionDialogButtonClickListener() {
          @Override
          public void onPositiveButtonClicked(MaterialButton button) {
            if (button.getText().equals(getString(R.string.settings))) {
              Util.openAppSettings(CameraActivity.this);
            } else {
              resultLauncher.launch(new String[] {permission});
            }
          }

          @Override
          public void onNegativeButtonClicked(MaterialButton button) {
            if (button.getText().equals(getString(R.string.exit))) {
              finish();
            }
          }
        });
  }

  private void init() {
    handler = new Handler();
    cameraController = new LifecycleCameraController(this);
    cameraExecutor = Executors.newSingleThreadExecutor();
    soundId = soundPool.load(this, R.raw.photo_click, 1);
    resultLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
              Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
              Boolean fineLocationGranted =
                  result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
              Boolean coarseLocationGranted =
                  result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
              if (cameraGranted != null && cameraGranted) {
                // camera access granted
                startCamera();
              } else {
                // re-check permission granted or not
                if (!Util.isPermissionGranted(this, Manifest.permission.CAMERA)) {
                  getDialog(Manifest.permission.CAMERA).show();
                }
              }

              if (fineLocationGranted != null && fineLocationGranted) {
                // Precise location access granted.
              } else if (coarseLocationGranted != null && coarseLocationGranted) {
                // Only approximate location access granted.
              } else {
                // No location access granted.
                // check is this is called for camera permission or not
                if (coarseLocationGranted != null && fineLocationGranted != null) {
                  Util.createPermissionSnackBar(this, binding.getRoot(), R.string.location_title);
                }
              }
            });
  }

  private void initViews() {
    metaDataBinding = binding.metaData;
    metaDataBinding.timeTv.setText(getString(R.string.time) + " " + Util.getCurrentTime());
    metaDataBinding.noteTv.setText(
        getString(R.string.note) + " " + Build.MODEL + "(" + Build.MANUFACTURER + ")");
    metaDataBinding.longitudeTv.setVisibility(View.GONE);
    metaDataBinding.latitudeTv.setVisibility(View.GONE);
    metaDataBinding.accuracyTv.setVisibility(View.GONE);
    // scheduling update time after every 1 minute
    timeUpdater.run();
  }

  private void onClickEvents() {
    binding.capture.setOnClickListener(view -> takePhoto());
    binding.photos.setOnClickListener(v -> Util.showShortToast(this, "photos clicked"));
    binding.settings.setOnClickListener(v -> Util.showShortToast(this, "Settings clicked"));
    binding.switchCamera.setOnClickListener(v -> switchCamera());
    binding.flashImage.setOnClickListener(v -> Util.showShortToast(this, "flash clicked"));
  }

  private void requestPermission(String permission) {
    resultLauncher.launch(new String[] {permission});
  }

  private void requestPermissions(String[] permissions) {
    // Before you perform the actual permission request, check whether your app
    // already has the permissions, and whether your app needs to show a permission
    // rationale dialog. For more details, see Request permissions.
    resultLauncher.launch(permissions);
  }

  private void setFullScreen() {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
  }

  private void startCamera() {
    // check location is granted or not if not snack-bar is shown to user
    requestLocationPermission();

    Runnable listener =
        () -> {
          // Preview
          Preview preview = new Preview.Builder().build();
          preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
          binding.viewFinder.setController(cameraController);
          // Select back camera as a default
          cameraController.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
          try {
            // Unbind before rebinding
            cameraController.unbind();
            // Bind use cases to camera
            cameraController.bindToLifecycle(CameraActivity.this);
          } catch (IllegalStateException e) {
            Log.e(TAG, "Use case binding failed", e);
          }
        };
    cameraController.setImageCaptureIoExecutor(cameraExecutor);
    cameraController
        .getInitializationFuture()
        .addListener(listener, ContextCompat.getMainExecutor(this));
  }

  private void requestLocationPermission() {
    if (!Util.isPermissionGranted(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        && !Util.isPermissionGranted(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
          });
    }
  }

  private void switchCamera() {
    try {
      if (cameraController.getCameraSelector() == CameraSelector.DEFAULT_BACK_CAMERA
          && cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
        cameraController.setCameraSelector(CameraSelector.DEFAULT_FRONT_CAMERA);
      } else if (cameraController.getCameraSelector() == CameraSelector.DEFAULT_FRONT_CAMERA
          && cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
        cameraController.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
      }
    } catch (IllegalStateException e) {
      Log.d(TAG, e.getMessage());
    }
  }

  private void takePhoto() {
    cameraController.takePicture(
        ContextCompat.getMainExecutor(this),
        new ImageCapture.OnImageCapturedCallback() {
          @Override
          public void onCaptureSuccess(@NonNull ImageProxy image) {
            super.onCaptureSuccess(image);
            soundPool.play(soundId, 1, 1, 0, 0, 1);
            Bitmap res = Util.addWaterMark(Util.getBitmap(image), location);
            Util.saveImage(CameraActivity.this, res);
          }
        });
  }
}
