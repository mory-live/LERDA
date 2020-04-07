package jp.co.saisentan.morymvp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

public class CameraRecordActivity extends AppCompatActivity {

    private SurfaceView surfaceView;
    private MainSurfaceView mainSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_record);

        surfaceView = (SurfaceView)findViewById(R.id.svMain);
        mainSurfaceView = new MainSurfaceView(this, surfaceView);

        Button btClick = findViewById(R.id.btAlert);
        AlertButtonListener listener = new AlertButtonListener();
        btClick.setOnClickListener(listener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainSurfaceView.onPause();
    }

    private class AlertButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(CameraRecordActivity.this, StreamActivity.class);
            startActivity(intent);
        }
    }
}
