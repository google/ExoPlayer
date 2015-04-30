package com.google.android.exoplayer.demo;

import com.google.android.exoplayer.demo.Samples.Sample;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;


/**
 * Created by wenche1x on 4/28/2015.
 */
public class LocalActivity extends Activity{
    private static final String TAG = "LocalActivity";
    ArrayList<VideoInfo> VideoList = new ArrayList<LocalActivity.VideoInfo>();
    Sample Local[] ;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.local_activity);
        initLocal();
        System.out.println("add videolist ");
        Local = new Sample[VideoList.size()];
        for(int i=0; i< VideoList.size();i++){
            Local[i] = new Sample(VideoList.get(i).title,"file://"+ VideoList.get(i).filePath,DemoUtil.TYPE_OTHER);
        }
        ListView local_list = (ListView) findViewById(R.id.local_list);
        final LocalAdapter localAdapter = new LocalAdapter(this);

        System.out.println("added local playback Header ");
        localAdapter.add(new Header("Local Playback"));
        localAdapter.addAll((Object[]) Local);

        local_list.setAdapter(localAdapter);
        local_list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = localAdapter.getItem(position);
                if (item instanceof Sample) {
                    onLocalSelected((Sample) item);
                }
            }
        });
    }

    private void initLocal(){
        String[] thumbColumns = new String[]{
                MediaStore.Video.Thumbnails.DATA,
                MediaStore.Video.Thumbnails.VIDEO_ID
        };

        String[] mediaColumn = new String[]{
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.MIME_TYPE
        };

        ContentResolver contentResolver = this.getContentResolver();
        Cursor cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,mediaColumn,null,null,MediaStore.Video.Media.DEFAULT_SORT_ORDER);

        if(cursor.moveToFirst()){
            do{
                VideoInfo info = new VideoInfo();

                info.filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                info.mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
                info.title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));

                int id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                String selection = MediaStore.Video.Thumbnails.VIDEO_ID + "=?";
                String[] selectionArgs = new String[]{
                        id+""
                };
                Cursor thumbCursor = contentResolver.query(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,thumbColumns,selection,selectionArgs,null);

                if(thumbCursor.moveToFirst()){
                    info.thumbPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA));
                }

                VideoList.add(info);
                System.out.printf("local media: %s\n", info.title);
            }while(cursor.moveToNext());

        }
        System.out.println("local media file init");
    }

    private void onLocalSelected(Sample sample) {
        Intent mpdIntent = new Intent(this, PlayerActivity.class)
                .setData(Uri.parse(sample.uri))
                .putExtra(PlayerActivity.CONTENT_ID_EXTRA, sample.contentId)
                .putExtra(PlayerActivity.CONTENT_TYPE_EXTRA, sample.type);
        startActivity(mpdIntent);
    }
    private static class LocalAdapter extends ArrayAdapter<Object> {

        public LocalAdapter(Context context) {
            super(context,0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                int layoutId = getItemViewType(position) == 1 ? android.R.layout.simple_list_item_1
                        : R.layout.sample_chooser_inline_header;
                view = LayoutInflater.from(getContext()).inflate(layoutId, null, false);
            }
            Object item = getItem(position);
            String name = null;
            if (item instanceof Samples.Sample) {
                name = ((Samples.Sample) item).name;
            } else if (item instanceof Header) {
                name = ((Header) item).name;
            }
            ((TextView) view).setText(name);
            return view;
        }

        @Override
        public int getItemViewType(int position) {
            return (getItem(position) instanceof Sample) ? 1 : 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

    }

    private static class Header {

        public final String name;

        public Header(String name) {
            this.name = name;
        }

    }

    static class VideoInfo{
        String filePath;
        String mimeType;
        String thumbPath;
        String title;
    }
}
