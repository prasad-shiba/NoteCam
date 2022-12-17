package com.demo.notecam;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.camera.core.ImageProxy;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

public final class Util {
  public static final int REQUEST_PERMISSION_SETTING = 12;
  private static final String DIRECTORY_NAME = "My Note Camera";

  private Util() {}

  public static Bitmap addWaterMark(Bitmap src, Location location) {
    int w = src.getWidth();
    int h = src.getHeight();
    Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());

    Canvas canvas = new Canvas(result);
    canvas.drawBitmap(src, 0, 0, null);

    // for white rectangular draw
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);

    // for text draw
    TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    textPaint.setColor(Color.BLACK);
    textPaint.setTextSize(80);
    textPaint.setWordSpacing(0.1F);

    // order is important
    // first white rectangle draw then written above white rectangle
    if (location != null) {
      canvas.drawRect(0, h - 550, 1000, h, paint);
      canvas.drawText("Latitude : ", 50, h - 450, textPaint);
      canvas.drawText("Longitude : ", 50, h - 350, textPaint);
      canvas.drawText("Accuracy : ", 50, h - 250, textPaint);
    } else {
      canvas.drawRect(0, h - 250, 1000, h, paint);
    }

    canvas.drawText("Time : " + getCurrentTime(), 50, h - 150, textPaint);
    canvas.drawText(
        "Note : " + Build.MODEL + "(" + Build.MANUFACTURER + ")", 50, h - 50, textPaint);

    return result;
  }

  public static Bitmap getBitmap(ImageProxy imageProxy) {
    ByteBuffer byteBuffer = imageProxy.getPlanes()[0].getBuffer();
    byte[] bytes = new byte[byteBuffer.capacity()];
    byteBuffer.get(bytes);
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
  }

  public static boolean isPermissionGranted(Context context, String permission) {
    return ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED;
  }

  public static void showShortToast(Activity activity, String msg) {
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
  }

  public static boolean isLocationPermissionGranted(Context context) {
    return isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
  }

  public static boolean isCameraPermissionGranted(Context context) {
    return isPermissionGranted(context, Manifest.permission.CAMERA);
  }

  public static Dialog createPermissionDialog(
      CameraActivity activity, String permission, OnPermissionDialogButtonClickListener listener) {
    boolean isFinishWhenBackPressed = false;
    Dialog dialog = new Dialog(activity);
    dialog.setContentView(R.layout.permission_dialog);
    dialog
        .getWindow()
        .setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    MaterialButton posBtn = dialog.findViewById(R.id.pos_btn);
    MaterialButton negBtn = dialog.findViewById(R.id.neg_btn);
    MaterialTextView descTv = dialog.findViewById(R.id.message_tv);
    MaterialTextView titleTv = dialog.findViewById(R.id.title_tv);

    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
      Util.showShortToast(activity, "show");
    } else {
      Util.showShortToast(activity, "don't show");
    }

    if (Manifest.permission.CAMERA.equals(permission)) {
      negBtn.setText(R.string.exit);
      titleTv.setText(R.string.camera_title);
      isFinishWhenBackPressed = true;
      if (activity.shouldShowRequestPermissionRationale(permission)) {
        posBtn.setText(R.string.allow);
        descTv.setText(R.string.camera_description);
      } else {
        posBtn.setText(R.string.settings);
        descTv.setText(R.string.camera_description_after_deny);
      }
    } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
      negBtn.setText(R.string.deny);
      titleTv.setText(R.string.storage_title);
      if (activity.shouldShowRequestPermissionRationale(permission)) {
        posBtn.setText(R.string.allow);
        descTv.setText(R.string.storage_description);
      } else {
        posBtn.setText(R.string.settings);
        descTv.setText(R.string.storage_description_after_deny);
      }
    } else {
      negBtn.setText(R.string.deny);
      titleTv.setText(R.string.location_title);
      if (activity.shouldShowRequestPermissionRationale(permission)) {
        posBtn.setText(R.string.allow);
        descTv.setText(R.string.location_description);
      } else {
        posBtn.setText(R.string.settings);
        descTv.setText(R.string.location_description_after_deny);
      }
    }

    posBtn.setOnClickListener(
        v -> {
          listener.onPositiveButtonClicked(posBtn);
          dialog.dismiss();
        });
    negBtn.setOnClickListener(
        v -> {
          listener.onNegativeButtonClicked(negBtn);
          dialog.dismiss();
        });

    dialog.setCancelable(false);
    // dismiss dialog when back key pressed
    boolean finalIsFinishWhenBackPressed = isFinishWhenBackPressed;
    dialog.setOnKeyListener(
        (dialog1, keyCode, event) -> {
          if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            dialog.dismiss();
            if (finalIsFinishWhenBackPressed) {
              activity.finish();
            }
          }
          return true;
        });

    return dialog;
  }

  public static String getCurrentTime() {
    return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        .format(Calendar.getInstance());
  }

  public static void saveImage(Context context, Bitmap bitmap) {
    // Create time stamped name and MediaStore entry.
    final ContentValues contentValues = new ContentValues();
    contentValues.put(
        MediaStore.MediaColumns.DISPLAY_NAME, String.valueOf(System.currentTimeMillis()));
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
    contentValues.put(
        MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + DIRECTORY_NAME);
    final ContentResolver resolver = context.getContentResolver();
    Uri uri = null;
    try {
      final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
      uri = resolver.insert(contentUri, contentValues);

      if (uri == null) throw new IOException("Failed to create new MediaStore record.");

      try (final OutputStream stream = resolver.openOutputStream(uri)) {
        if (stream == null) throw new IOException("Failed to open output stream.");

        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
          throw new IOException("Failed to save bitmap.");
      }
    } catch (IOException e) {

      if (uri != null) {
        // Don't leave an orphan entry in the MediaStore
        resolver.delete(uri, null, null);
      }
    }
  }

  private boolean isAllPermissionGranted(Context context) {
    return isCameraPermissionGranted(context) && isLocationPermissionGranted(context);
  }

  public static void createPermissionSnackBar(Activity activity, View view, @StringRes int text) {
    Snackbar.make(view, text, Snackbar.LENGTH_LONG)
        .setAction(R.string.settings, v -> openAppSettings(activity))
        .show();
  }

  public static void openAppSettings(Activity activity) {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.parse("package:" + activity.getPackageName()));
    ActivityCompat.startActivityForResult(activity, intent, REQUEST_PERMISSION_SETTING, null);
  }
}
