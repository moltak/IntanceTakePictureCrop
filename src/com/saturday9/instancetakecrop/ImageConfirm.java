package com.saturday9.instancetakecrop;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

public class ImageConfirm extends Activity {
	private ImageView mImageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_confirm);
		mImageView = (ImageView)findViewById(R.id.imageView1);
	}
	
	@Override
	protected void onStart() {
		super.onResume();
		
		Bitmap bitmap = BitmapFactory.decodeFile(getIntent().getExtras().getString("saved_img"));
		mImageView.setImageBitmap(bitmap);
	}
}