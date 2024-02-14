package checks;

import android.hardware.camera2.CameraDevice;
import android.media.MediaPlayer;
import android.media.MediaRecorder;

public class ReleaseSensorsCheckSample_compliant {

  public static class MockedSensorsExample {
    public void acquireSensors(
      android.location.LocationManager locationManager,
      android.hardware.SensorManager sensorManager,
      android.net.wifi.WifiManager.MulticastLock multicastLock,
      android.hardware.camera2.CameraManager cameraManager) {

      locationManager.requestLocationUpdates();
      sensorManager.registerListener();
      multicastLock.acquire();
      android.hardware.Camera camera = android.hardware.Camera.open(1);
      android.media.MediaPlayer mediaPlayer = new MediaPlayer();
      android.media.MediaRecorder mediaRecorder = new MediaRecorder();

      cameraManager.openCamera("id",
        new android.hardware.camera2.CameraDevice.StateCallback() {
          @Override
          public void onDisconnected(CameraDevice camera) {
            camera.close();
          }

          @Override
          public void onError(CameraDevice camera, int error) {
            camera.close();
          }

          @Override
          public void onOpened(android.hardware.camera2.CameraDevice camera) {
            // mock implementation
          }
        },
        null);
    }

    public void releaseSensors(
      android.location.LocationManager locationManager,
      android.hardware.SensorManager sensorManager,
      android.net.wifi.WifiManager.MulticastLock multicastLock,
      android.hardware.Camera camera,
      android.media.MediaPlayer mediaPlayer,
      android.media.MediaRecorder mediaRecorder) {

      camera.release();
      locationManager.removeUpdates();
      sensorManager.unregisterListener();
      multicastLock.release();
      mediaPlayer.release();
      mediaRecorder.release();
    }
  }

  public static class FakeSensorExample {
    public void test() {
      FakeSensor fakeSensor = new FakeSensor();
      fakeSensor.acquire(); // Compliant
    }

    public static class FakeSensor {
      public void acquire() {
        // mock implementation
      }

      public void release() {
        // mock implementation
      }
    }
  }
}
