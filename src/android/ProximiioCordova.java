package com.navtureapps.cordovaplugin;

import org.apache.cordova.*;
import org.json.JSONException;

import android.widget.Toast;
import android.app.Activity;
import android.util.Log;
import android.content.Intent;
import java.lang.Runnable;
import org.json.JSONObject;
import android.support.annotation.Nullable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import io.proximi.proximiiolibrary.ProximiioAPI;
import io.proximi.proximiiolibrary.ProximiioBLEDevice;
import io.proximi.proximiiolibrary.ProximiioEddystone;
import io.proximi.proximiiolibrary.ProximiioGeofence;
import io.proximi.proximiiolibrary.ProximiioIBeacon;
import io.proximi.proximiiolibrary.ProximiioInput;
import io.proximi.proximiiolibrary.ProximiioListener;
import io.proximi.proximiiolibrary.ProximiioFloor;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;

public class ProximiioCordova extends CordovaPlugin implements OnRequestPermissionsResultCallback {
  private ProximiioAPI proximiio;
  private ProximiioListener listener;
  private String id;
  private String token;
  private boolean handlePush = true;
  private boolean enableDebug = false;

  private Activity activity;
  private static final String TAG = "ProximiioCordova";

  private CallbackContext context;

  private static final String ACTION_SET_TOKEN = "setToken";
  private static final String ACTION_ENABLE_DEBUG = "enableDebug";
  private static final String ACTION_HANDLE_PUSH = "handlePush";
  private static final String ACTION_REQUEST_PERMISSIONS = "requestPermissions";
  private static final String ACTION_START_SCANNING = "startScanning";
  private static final String ACTION_STOP_SCANNING = "stopScanning";

  private String [] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };

  @Override
  public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    activity = cordova.getActivity();
    context = callbackContext;
    cordova.setActivityResultCallback(this);
    if (action.equals(ACTION_SET_TOKEN)) {
        if (proximiio == null) {
          token = args.getString(0);
          initProximiio();
        }
    } else if (action.equals(ACTION_ENABLE_DEBUG)) {
      String value = args.getString(0);
      enableDebug = value.equals("true");
      log("execute", "Debug mode enabled");      
    } else if (action.equals(ACTION_HANDLE_PUSH)) {
      String value = args.getString(0);
      handlePush = value.equals("true");
    } else if (action.equals(ACTION_START_SCANNING)) {
      if (proximiio != null) {
        initProximiio();
      }
    } else if (action.equals(ACTION_STOP_SCANNING)) {
      if (proximiio != null) {
        proximiio.destroy(false);
      }
    } else if (action.equals(ACTION_REQUEST_PERMISSIONS)) {
      if (proximiio != null) {
        // proximiio.checkPermissions(); Obsolete in Android Proximiio.SDK 2.5
      }
    }
    PluginResult r = new PluginResult(PluginResult.Status.OK);          
    context.sendPluginResult(r);
    return true;
  }

  private void initProximiio() {
    proximiio = new ProximiioAPI("ProximiioCordovaAPI", activity);
    proximiio.setActivity(activity);
    listener = new ProximiioListener() {
      @Override
      public void geofenceEnter(final ProximiioGeofence geofence) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              String geofenceJson = geofence.getJSON().toString();
              String action = "javascript:proximiio.triggeredGeofence(1, " + geofenceJson + ")";
              log("geofenceEnter", action);
              loadUrl(action);
            }
        });
      }

      @Override
      public void geofenceExit(final ProximiioGeofence geofence, @Nullable Long dwellTime) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String geofenceJson = geofence.getJSON().toString();
            String action = "javascript:proximiio.triggeredGeofence(0, " + geofenceJson + ")";
            log("geofenceExit", action);
            loadUrl(action);
          }
        });
      }

      @Override
      public void changedFloor(@Nullable ProximiioFloor floor) {
        if (floor != null) {
          final String floorJson = floor.getJSON().toString();
          activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  String action = "javascript:proximiio.changedFloor(0, " + floorJson + ")";
                  log("changedFloor", action);
                  loadUrl(action);
                }
          });
        }
      }

      @Override
      public void position(final double lat, final double lon, final double accuracy) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String json = "{\"coordinates\":{\"lat\":" + lat + ", \"lon\":" + lon + "}, \"accuracy\":" + accuracy + "}";
            String action = "javascript:proximiio.updatedPosition(" + json + ")";
            if (enableDebug) {
              log("position", action);
            }
            loadUrl(action);
          }
        });
      }

      @Override
      public void loggedIn(boolean online) {
        super.loggedIn(online);
        Log.e(TAG, "loggedIn! (" + online + ")");
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String action = "javascript:proximiio.proximiioReady(\"" + proximiio.getVisitorID() + "\")";
            log("initProximiio", action);
            if (enableDebug) {
              Toast.makeText(activity, "Proximi.io Initialized!", Toast.LENGTH_SHORT).show();
            }
            loadUrl(action);
          }
        });
      }

      @Override
      public void loginFailed(LoginError loginError) {
        super.loginFailed(loginError);
        Log.e(TAG, "LoginError! (" + loginError.toString() + ")");
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String action = "javascript:proximiio.proximiioReady(null)";
            log("initProximiio", action);
            if (enableDebug) {
              Toast.makeText(activity, "Proximi.io Authentication Failure!", Toast.LENGTH_SHORT).show();
            }
            loadUrl(action);
          }
        });
      }


      /**
       * Push output from Proximiio
       * @param title Text received
       * @return Is this push handled? (If not, Proximiio generates a snackbar)
       */
      @Override
      public boolean push(String title) {
        return false;
      }

      /**
      * Receives JSON payloads from Proximiio events outputs.
      * @param json JSON received as specified in payload.
      */
      @Override
      public void output(final JSONObject json) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String action = "javascript:proximiio.triggeredOutput(" + json.toString() + ")";
            log("output", action);
            loadUrl(action);
          }
        });
      }

      private String getJSONForBLEDevice(ProximiioBLEDevice beacon) {
        String json = "{}";
        if (beacon.getType() == ProximiioInput.InputType.IBEACON) {
          ProximiioIBeacon iBeacon = (ProximiioIBeacon)beacon;
          json = "{\"name\": \"Unknown Beacon\", \"accuracy\": "+ beacon.getDistance() + ",\"uuid\": \"" + iBeacon.getUUID() +"\", \"major\": " + iBeacon.getMajor() + ", \"minor\": " + iBeacon.getMinor() + "}";
        } else if (beacon.getType() == ProximiioInput.InputType.EDDYSTONE) {
          ProximiioEddystone eddystone = (ProximiioEddystone) beacon;
          json = "{\"name\": \"Unknown Beacon\", \"accuracy\": "+ beacon.getDistance() +  ", \"namespace\": \"" + eddystone.getNamespace() + "\", \"instance\": \"" + eddystone.getInstanceID() + "\"}";
        }
        return json;
      }

      @Override
      public void foundDevice(final ProximiioBLEDevice beacon, final boolean registered) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String action = "";            
            if (registered) {
              JSONObject json = null;
              try {
                json = new JSONObject(beacon.getInput().getJSON());
                json.put("accuracy", beacon.getDistance());
              } catch (JSONException e) {
                e.printStackTrace();
              }

              if (json != null) {
                action = "javascript:proximiio.foundBeacon(" + json.toString() + ")";
              }
            } else {
              action = "javascript:proximiio.foundBeacon(" + getJSONForBLEDevice(beacon) + ")";
            }
            log("foundBeacon", action);
            loadUrl(action);
          }
        });
      }

      @Override
      public void lostDevice(final ProximiioBLEDevice beacon, final boolean registered) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String action = "";
            if (registered) {
              JSONObject json = null;
              try {
                json = new JSONObject(beacon.getInput().getJSON());
                json.put("accuracy", beacon.getDistance());
              } catch (JSONException e) {
                e.printStackTrace();
              }

              if (json != null) {
                action = "javascript:proximiio.lostBeacon(" + json.toString() + ")";
              }
            } else {
              action = "javascript:proximiio.lostBeacon(" + getJSONForBLEDevice(beacon) + ")";
            }
            log("lostBeacon", action);
            loadUrl(action);
          }
        });
      }
    };

    proximiio.setListener(listener);
    proximiio.setAuth(token);
  }

  private void log(String method, String action) {
    if (enableDebug) {
      Log.d(TAG, method + ": " + action);
    }
  }

  @Override
  public void onStart() {
    activity = cordova.getActivity();
    if (id != null && token != null) {
      initProximiio();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (proximiio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      activity.onRequestPermissionsResult(requestCode, permissions, grantResults);
      proximiio.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      if (proximiio != null) {
        proximiio.onActivityResult(requestCode, resultCode, data);
      }
  }

  public void onRequestPermissionResult(int requestCode, String[] permissions,
                                        int[] grantResults) throws JSONException {
      PluginResult result;
      for(int r:grantResults) {
          if(r == PackageManager.PERMISSION_DENIED) {
              Log.d(TAG, "Permission Denied!");
              result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
              context.sendPluginResult(result);
              return;
          }
      }
      result = new PluginResult(PluginResult.Status.OK);
      initProximiio();
      context.sendPluginResult(result);
  }

  private void loadUrl(String url) {
    if (webView != null) {
      webView.loadUrl(url);
    }
  }

  public boolean hasPermisssion() {
      for(String p : permissions) {
          if(!PermissionHelper.hasPermission(this, p)) {
              return false;
          }
      }
      return true;
  }

  /*
   * We override this so that we can access the permissions variable, which no longer exists in
   * the parent class, since we can't initialize it reliably in the constructor!
   */

  public void requestPermissions(int requestCode) {
      PermissionHelper.requestPermissions(this, requestCode, permissions);
  }
}
