package jp.co.saisentan.morymvp;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

public class StreamActivity extends Activity {
    private static final String TAG = StreamActivity.class.getSimpleName();

    //
    // Set your APIkey and Domain
    //
    private static final String API_KEY = "6b73417c-ed9c-4d2f-832b-c689e92cd117";
    private static final String DOMAIN = "localhost";

    private Peer			_peer;
    private MediaStream		_localStream;
    private MediaConnection	_mediaConnection;
    private String			_strOwnId;


    public void onClickButton(View view) {
        //Log.d(TAG, "*** FINISH!!! ***");
        finish();
    }

    public enum	CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }
    private CallState		_callState;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window wnd = getWindow();
        wnd.addFlags(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_stream);

        _callState = CallState.TERMINATED;

        //for sending email
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //
        // Initialize Peer
        //
        PeerOption option = new PeerOption();
        option.key = API_KEY;
        option.domain = DOMAIN;
        //option.hwcodec = false;
        _peer = new Peer(this, option);

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {

                // Show my ID
                _strOwnId = (String) object;
                TextView tvOwnId = (TextView) findViewById(R.id.tvOwnId);
                tvOwnId.setText(_strOwnId);

                sendMail();

                // Get a local MediaStream & show it
                startLocalStream();
            }
        });

        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof MediaConnection)) {
                    return;
                }

                _mediaConnection = (MediaConnection) object;
                _callState = CallState.CALLING;

                _mediaConnection.answer(_localStream);
                setMediaCallbacks();
                _callState = CallState.ESTABLISHED;
            }
        });

        _peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback()	{
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Close]");
            }
        });
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Disconnected]");
            }
        });
        _peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/Error]" + error);
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Disable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set volume control stream type to WebRTC audio.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        // Set default volume control stream type.
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        super.onPause();
    }

    @Override
    protected void onStop()	{
        // Enable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyPeer();
        super.onDestroy();
    }

    //
    // Get a local MediaStream & show it
    //
    void startLocalStream() {
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        constraints.maxWidth = 640;
        constraints.maxHeight = 480;
        constraints.cameraPosition = MediaConstraints.CameraPositionEnum.BACK;
        _localStream = Navigator.getUserMedia(constraints);

        Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
        _localStream.addVideoRenderer(canvas,0);
    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, new OnCallback() {
            @Override
            public void onCallback(Object object) {

            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback()	{
            @Override
            public void onCallback(Object object) {
                _callState = CallState.TERMINATED;
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, new OnCallback()	{
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/MediaError]" + error);
            }
        });

    }

    //
    // Clean up objects
    //
    private void destroyPeer() {

        if (null != _localStream) {
            Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
            _localStream.removeVideoRenderer(canvas,0);
            _localStream.close();
        }

        closeMediaConnection();
        Navigator.terminate();

        if (null != _peer) {
            unsetPeerCallback(_peer);
            if (!_peer.isDisconnected()) {
                _peer.disconnect();
            }

            if (!_peer.isDestroyed()) {
                _peer.destroy();
            }

            _peer = null;
        }
    }

    //
    // Unset callbacks for PeerEvents
    //
    void unsetPeerCallback(Peer peer) {
        if(null == _peer){
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }

    //
    // Unset callbacks for MediaConnection.MediaEvents
    //
    void unsetMediaCallbacks() {
        if(null == _mediaConnection){
            return;
        }

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, null);
    }

    //
    // Close a MediaConnection
    //
    void closeMediaConnection() {
        if (null != _mediaConnection)	{
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close();
            }
            unsetMediaCallbacks();
        }
    }

    public void sendMail() {
        final String g_account = "dev@mory.live";
        final String password = "uTWC#Vh4Y}Dq3utf";
        String body = "PeerID： " + _strOwnId + "\n以下のアドレスにブラウザでアクセスして上記のPeerIDを入力して下さい。\nhttps://mory-live.github.io/mory/";
        String subject = "MORY：アラートボタンが押されました";

        try {
            //read email address from file
            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MORY");
            File ma_file = new File(mediaStorageDir.getPath() + File.separator + "mail_address.txt");
            if (!ma_file.exists()) {
                //ファイルが存在しない
                return;
            }
            FileInputStream fileInputStream = new FileInputStream(ma_file);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, "UTF8");
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String email_address;
            email_address = reader.readLine();
            if( email_address == null ) {
                //emailアドレスが設定されていない
                return;
            }
            reader.close();

            final Properties property = new Properties();
            property.put("mail.smtp.host", "smtp.gmail.com");
            property.put("mail.host", "smtp.gmail.com");
            property.put("mail.smtp.port", "465");
            property.put("mail.smtp.socketFactory.port", "465");
            property.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

            // セッション
            final Session session = Session.getInstance(property, new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(g_account, password);
                }
            });

            MimeMessage mimeMsg = new MimeMessage(session);
            mimeMsg.setSubject(subject, "utf-8");
            mimeMsg.setFrom(new InternetAddress(g_account));
            mimeMsg.setRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(email_address));

            final MimeBodyPart txtPart = new MimeBodyPart();
            txtPart.setText(body, "utf-8");

            final Multipart mp = new MimeMultipart();
            mp.addBodyPart(txtPart);
            mimeMsg.setContent(mp);

            // メール送信
            final Transport transport = session.getTransport("smtp");
            transport.connect(g_account,password);
            transport.sendMessage(mimeMsg, mimeMsg.getAllRecipients());
            transport.close();

        } catch (MessagingException e) {
            System.out.println("exception = " + e);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("finish sending email");
        }
    }

}
