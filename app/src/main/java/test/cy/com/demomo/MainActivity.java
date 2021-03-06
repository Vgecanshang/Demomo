package test.cy.com.demomo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cy.cylibrary.DynamicPermission.ApplyPermissionUtil;
import com.cy.cylibrary.Welcome;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private AsyncHttpServer server = new AsyncHttpServer();
    private AsyncServer mAsyncServer = new AsyncServer();
    private static final int TYPE_EXTERNAL_STORAGE = 110;
    Button click_btn , stop_btn;
    TextView show_tv;
    private boolean isOpenService = false;//是否开启服务
    private ApplyPermissionUtil permissionUtil = null;//三方动态申请权限工具类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionUtil = new ApplyPermissionUtil(MainActivity.this, requestPermissionsListener);

        click_btn = findViewById(R.id.click_btn);
        stop_btn = findViewById(R.id.stop_btn);
        show_tv = findViewById(R.id.show_tv);

        click_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isOpenService){
                    getPhoneAddress();
                }else{
                    show_tv.setText("文件共享服务已停止...");
                }
            }
        });
        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(isOpenService){
                    stopListener();
                }else{
                    getPhoneAddress();
                    listenServices();
                }

            }
        });

        /*
        * 这里有一个问题，因为我的targetSdkVersion是26 即Android6.0以上的系统，将会设计到动态权限问题
        * 这里需要的的敏感全是读写文件的权限
        * 先获取读写权限(后面的通过浏览器读取手机的文件需要)
        * */
        permissionUtil.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, TYPE_EXTERNAL_STORAGE);


//        Welcome.sayHello(this);

    }

    /* 获取手机IP */
    private void getPhoneAddress(){
        String phoneIp = "";
        NetworkInfo info = ((ConnectivityManager) MainActivity.this
                .getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {//当前使用2G/3G/4G网络
                try {
                    //Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                phoneIp  = inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }

            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {//当前使用无线网络
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());//得到IPV4地址
                phoneIp =  ipAddress;
            }
        } else {
            //当前无网络连接,请在设置中打开网络
            show_tv.setText("当前无网络连接,请在设置中打开网络");
            return;
        }

        show_tv.setText(phoneIp+":54321");

    }



    /*开启文件共享服务*/
    private void listenServices(){
        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try {
                    response.send(getIndexContent());
                } catch (IOException e) {
                    e.printStackTrace();
                    response.code(500).end();
                }
            }
        });

        /*这里用了jquery，在这里也要对js的请求也做处理*/
        server.get("/jquery-1.7.2.min.js", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try {
                    String fullPath = request.getPath();
                    fullPath = fullPath.replace("%20", " ");
                    String resourceName = fullPath;
                    if (resourceName.startsWith("/")) {
                        resourceName = resourceName.substring(1);
                    }
                    if (resourceName.indexOf("?") > 0) {
                        resourceName = resourceName.substring(0, resourceName.indexOf("?"));
                    }
                    response.setContentType("application/javascript");
                    BufferedInputStream bInputStream = new BufferedInputStream(getAssets().open(resourceName));
                    response.sendStream(bInputStream, bInputStream.available());
                } catch (IOException e) {
                    e.printStackTrace();
                    response.code(404).end();
                    return;
                }
            }
        });


        server.get("/files", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {


                JSONArray array = new JSONArray();
                System.out.println("path = " + Environment.getExternalStorageDirectory().getPath()+"/Pictures/Screenshots");
                File dir = new File(Environment.getExternalStorageDirectory().getPath()+"/Pictures/Screenshots");
                String[] fileNames = dir.list();
                if(fileNames != null){
                    for (String fileName: fileNames) {
                        File file = new File(dir , fileName);
                        if(file.exists() && file.isFile() && (file.getName().endsWith(".png") || file.getName().endsWith(".jpg") || file.getName().endsWith(".mp3"))){
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("name", fileName);
                                obj.put("path", file.getAbsolutePath());
                                array.put(obj);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                response.send(array.toString());
            }
        });


        server.get("/files/.*", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String path = request.getPath().replace("/files/", "");
                try {
                    path = URLDecoder.decode(path, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    try {
                        FileInputStream fis = new FileInputStream(file);
                        response.sendStream(fis, fis.available());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }
                response.code(404).send("Not found!");
            }
        });

        server.listen(mAsyncServer, 54321);
        isOpenService = true;
        stop_btn.setText("停止");
    }

    private void stopListener(){
        if (server != null) {
            server.stop();
        }
        if (mAsyncServer != null) {
            mAsyncServer.stop();
        }
        isOpenService = false;
        show_tv.setText("文件共享服务已停止...");
        stop_btn.setText("开启");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
        if (mAsyncServer != null) {
            mAsyncServer.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionUtil.listenerRequestPermissionsResult(requestCode , permissions , grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    ApplyPermissionUtil.RequestPermissionsListener requestPermissionsListener = new ApplyPermissionUtil.RequestPermissionsListener() {
        @Override
        public void getRequestPermissionResult(boolean b, int i) {
            switch(i){
                case TYPE_EXTERNAL_STORAGE:
                    if(b){
                        Toast.makeText(MainActivity.this , "获取文件读取权限成功..." , Toast.LENGTH_LONG).show();
                        getPhoneAddress();
                        listenServices();
                    }else{
                        Toast.makeText(MainActivity.this , "获取读写权限失败..." , Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
            }
        }
    };


    private String getIndexContent() throws IOException {
        BufferedInputStream bInputStream = null;
        try {
            bInputStream = new BufferedInputStream(getAssets().open("index.html"));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len = 0;
            byte[] tmp = new byte[10240];
            while ((len = bInputStream.read(tmp)) > 0) {
                baos.write(tmp, 0, len);
            }
            return new String(baos.toByteArray(), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (bInputStream != null) {
                try {
                    bInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }



    /**
     * 将得到的int类型的IP转换为String类型
     *
     * @param ip
     * @return
     */
    public static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }
}
