package com.zl.pluginnine;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

/**
 * 代理Activity  为了让 AMS 检测  可以正常通过
 */
public class ProxyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proxy);

        Toast.makeText(this,"我是代理的",Toast.LENGTH_LONG).show();
    }
}
