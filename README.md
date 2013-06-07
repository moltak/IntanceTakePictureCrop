Simple take a picture and crop.
===


####screenshots
![](https://raw.github.com/moltak/IntanceTakePictureCrop/master/image_1.png)
![](https://raw.github.com/moltak/IntanceTakePictureCrop/master/image_2.png)



#####You must add attributes in the AndroidManifest.xml
```xml
<!-- Tell the system this app requires OpenGL ES 2.0. -->
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />

<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```



#####And add below code int mainlayout.xml
```xml
<RelativeLayout
    android:id="@+id/layout_surface"
    android:layout_width="match_parent"
    android:layout_height="match_parent" >

    <SurfaceView
        android:id="@+id/surfaceview"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <view
        android:id="@+id/image"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_x="0dip"
        android:layout_y="0dip"
        class="com.saturday9.cropimage.CropImageView"
        android:background="@android:color/transparent" />
</RelativeLayout>
```



#####If you need more information, see the code.

