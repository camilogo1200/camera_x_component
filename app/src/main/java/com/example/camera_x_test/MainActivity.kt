package com.android.example.cameraxapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.camera_x_test.CaptureCameraPictureFragment
import com.example.camera_x_test.R
import com.example.camera_x_test.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        supportFragmentManager.setFragmentResultListener(
            CaptureCameraPictureFragment.CAPTURED_IMAGE_URI,
            this
        ) { key, bundle ->
            val image = bundle.getString(CaptureCameraPictureFragment.CAPTURED_IMAGE_URI)
            Toast.makeText(baseContext, image, Toast.LENGTH_LONG).show()
        }
    }
}
