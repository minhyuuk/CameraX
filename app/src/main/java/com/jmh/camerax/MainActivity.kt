package com.jmh.camerax

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.jmh.camerax.databinding.ActivityMainBinding
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // camera 권한 요청
        if (allPermissionsGranted()) {
            // 카메라 시작 함수
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // TAKE BUTTON 을 클릭 시 사진을 찍을 수 있게 설정
        binding.cameraCaptureButton.setOnClickListener { takePhoto() }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    // 사진 촬영 버튼을 눌렀을 때 호출되는 함수
    private fun takePhoto() {
        // imageCapture 값이 null이면 앱 crash
        val imageCapture = imageCapture ?: return

        // 파일 이름 고유성을 위해 타임 스탬프 추가
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT,Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        // photoFile 형식으로 파일 출력 방법 지정
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 이미지 저장 시 callback을 파라미터로 전달
        imageCapture.takePicture(
            outputOptions,ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "사진 촬영에 성공했습니다! : $savedUri"
                    Toast.makeText(baseContext,msg,Toast.LENGTH_SHORT).show()
                    Log.d(TAG,msg)
                }

                // 이미지 저장에 실패하면 Log 출력
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG,"사진 촬영에 실패했습니다. : ${exception.message}",exception)
                }

            }
        )
    }


    // 카메라 어플리케이션에서 뷰파인더는 사용자가 자신이 찍을 사진을 미리 볼 수 있도록 하기 위해 사용
    // CameraX의 Preview 클래스를 사용하여 뷰파인더를 구현할 수 있음
    // Preview를 사용하려면 먼저 구성을 정의한 다음 이 구성을 사용하여 use case의 객체를 생성해야 함
    // use case의 인스턴스를 만들어야 함
    // 반환된 인스턴스는 CameraX 라이프사이클에 바인딩

//    카메라 프리뷰 함수
    private fun startCamera() {

        // ProcessCameraProvider의 인스턴스를 생성
        // 카메라의 라이프 사이클을 라이프 사이클 소유자에 바인딩하는 데 사용
        // 이렇게 하면 카메라X가 라이프사이클을 인식하므로 카메라를 열고 닫을 필요가 없어짐
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // 카메라의 라이프 사이클을 어플리케이션 프로세스 내에서 LifecycleOwner에 바인딩 하기위해 사용
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // preview 객체를 초기화 하고 빌드한 뒤, 뷰파인더에서 surface provider를 가져와
            // preview에 설정한다.
            val preview = Preview.Builder()
                .build()
                .also {
                    //
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            // 후면 카메라를 기본 값으로 설정
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try{
                // cameraProvider가 아무것도 바인딩 되지 않게 설정 후
                cameraProvider.unbindAll()

                // cameraSelector 와 preview 객체를 cameraProvider에 바인딩
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

                // 에러 발생 시 catch를 통해 Log 찍음
            }catch (e : Exception){
                Log.e(TAG,"Use case binding을 실패했습니다.", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // 요청 코드가 올바른 코드인지(올바른 코드 == 10) 확인하고
        // 아니면 무시
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // 권한이 부여된다면 권한 부여
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // 부여X, Permission Denied Toast 띄움
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
