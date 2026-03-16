package com.example.androidv1;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用独立页面布局，避免与接入方现有 activity_main.xml 产生冲突。
        setContentView(R.layout.activity_courier_home);
    }
}
