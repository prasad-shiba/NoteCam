package com.demo.notecam;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Timer;
import java.util.TimerTask;

public class LocationUtil {

  private static final String TAG = "LocationUtil";
  private final Context context;
  private final LocationListener locationListenerGps;
  private final LocationListener locationListenerNetwork;
  private Timer timer;
  private LocationManager manager;
  private LocationResult result;
  private boolean gps_enabled = false;
  private boolean network_enabled = false;

  {
    locationListenerGps =
        new LocationListener() {
          public void onLocationChanged(Location location) {
            timer.cancel();
            result.gotLocation(location);
            manager.removeUpdates(this);
            manager.removeUpdates(locationListenerNetwork);
          }

          public void onProviderDisabled(String provider) {}

          public void onProviderEnabled(String provider) {}

          public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
    locationListenerNetwork =
        new LocationListener() {
          public void onLocationChanged(Location location) {
            timer.cancel();
            result.gotLocation(location);
            manager.removeUpdates(this);
            manager.removeUpdates(locationListenerGps);
          }

          public void onProviderDisabled(String provider) {}

          public void onProviderEnabled(String provider) {}

          public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
  }

  public LocationUtil(Context context) {
    this.context = context;
  }

  public boolean getLocation(LocationResult result) {
    //  use LocationResult callback class to pass location value from LocationUtil to user code.
    this.result = result;
    if (manager == null)
      manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

    // exceptions will be thrown if provider is not permitted.
    try {
      gps_enabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, e.getMessage());
    }
    try {
      network_enabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, e.getMessage());
    }

    // don't start listeners if no provider is enabled
    if (!gps_enabled && !network_enabled) return false;

    if (gps_enabled) {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
          == PackageManager.PERMISSION_GRANTED) {
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
      }
    }
    if (network_enabled) {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
          == PackageManager.PERMISSION_GRANTED) {
        manager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
      }
    }
    timer = new Timer();
    timer.schedule(new GetLastLocation(), 1000);
    return true;
  }

  public abstract static class LocationResult {
    public abstract void gotLocation(Location location);
  }

  class GetLastLocation extends TimerTask {
    @Override
    public void run() {
      manager.removeUpdates(locationListenerGps);
      manager.removeUpdates(locationListenerNetwork);

      Location net_loc = null, gps_loc = null;
      if (gps_enabled) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
          manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
      }
      if (network_enabled) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
          manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
      }
      // if there are both values use the latest one
      if (gps_loc != null && net_loc != null) {
        if (gps_loc.getTime() > net_loc.getTime()) result.gotLocation(gps_loc);
        else result.gotLocation(net_loc);
        return;
      }

      if (gps_loc != null) {
        result.gotLocation(gps_loc);
        return;
      }
      if (net_loc != null) {
        result.gotLocation(net_loc);
        return;
      }
      result.gotLocation(null);
    }
  }
}
