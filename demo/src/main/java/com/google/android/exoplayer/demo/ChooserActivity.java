package com.google.android.exoplayer.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * Created by wenche1x on 4/28/2015.
 */
public class ChooserActivity extends Activity{
    private static final String TAG = "ChooserActivity";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chooser_activity);
        Button local = (Button) super.findViewById(R.id.local);
        Button stream = (Button) super.findViewById(R.id.stream);
        ButtonLisener lisener = new ButtonLisener();
        local.setTag(1);
        local.setOnClickListener(lisener);
        stream.setTag(2);
        stream.setOnClickListener(lisener);
    }

    public class ButtonLisener implements View.OnClickListener{

        @Override
        public void onClick(View v){
            int tag = (Integer) v.getTag();
            Intent intent = new Intent();
            switch(tag){
                case 1:
                    System.out.println("Local Playback");
                    intent.setClass(ChooserActivity.this,LocalActivity.class);
                    startActivity(intent);
                    //finish();
                    break;
                case 2:
                    System.out.println("Streaming Playback");
                    intent.setClass(ChooserActivity.this,SampleChooserActivity.class);
                    startActivity(intent);
                    //finish();
                    break;
                default:
                    System.out.println("Error Occur");
                    break;
            }
        }
    }

}

