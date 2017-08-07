/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import com.google.android.exoplayer2.demo.Samples.Sample;
import com.google.android.exoplayer2.lifemedia.ContextInformation;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings.Secure;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity for selecting from a list of samples.
 */
public class SampleChooserActivity extends Activity {

  SampleAdapter sampleAdapter;
  private static final String TAG_SAMPLE = "SampleChooserActivity";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.sample_chooser_activity);

      Log.d(TAG_SAMPLE,"onCreate");

    final ListView sampleList = (ListView) findViewById(R.id.sample_list);

    final SampleAdapter sampleAdapter = new SampleAdapter(this);
    sampleList.setAdapter(sampleAdapter);
    this.sampleAdapter = sampleAdapter;

    ContextInformation.androidID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
      sampleAdapter.add(new Header("LifeMedia Contents"));
      sampleAdapter.addAll((Object[]) Samples.LIFE_MEDIA);

      try {
          refreshContentsList(sampleAdapter);

      } catch (IOException e) {
          e.printStackTrace();
      }

      sampleList.setOnItemLongClickListener(new OnItemLongClickListener(){

          @Override
          public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
              Object item = sampleAdapter.getItem(position);
              Sample sample = (Sample) item;

              AlertDialog.Builder sampleDialog = new AlertDialog.Builder(SampleChooserActivity.this);
              sampleDialog.setTitle(sample.name);

              LayoutInflater inflater = getLayoutInflater();
              View sampleView = inflater.inflate(R.layout.check_sample, null);
              sampleDialog.setView(sampleView);
              TextView sampleURL = (TextView) sampleView.findViewById(R.id.sampleURL);

              sampleURL.setText(sample.uri);

              final AlertDialog sampleAlertDialog = sampleDialog.create();
              sampleAlertDialog.show();

              return true;
          }

      });

      sampleList.setAdapter(sampleAdapter);
      sampleList.setOnItemClickListener(new OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
              Object item = sampleAdapter.getItem(position);
              if (item instanceof Sample) {
                  onSampleSelected((Sample) item);
              }
          }
      });

  }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mainmenu, menu);
        Log.d(TAG_SAMPLE,"onCreateOptionMenu");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG_SAMPLE,"onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.refresh:
                try {
                    refreshContentsList(sampleAdapter);

                }

                catch (IOException e) {
                    e.printStackTrace();
                }

                break;

            case R.id.addressSettings:
                serverSettings();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

  private static final class SampleAdapter extends ArrayAdapter<Object> {

    public SampleAdapter(Context context) {
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

          if (item instanceof Sample) {
              name = ((Sample) item).name;
          }

          else if (item instanceof Header) {
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

    public void refreshContentsList(SampleAdapter sampleAdapter) throws IOException{
        Log.d(TAG_SAMPLE,"refreshContentsList");
        List<Sample> sampleListFromServer = new ArrayList<Sample>();

        if(getServerURL("ServerURL").equals(null) && getServerURL("ServerURL").equals("")){
            Toast.makeText(getApplicationContext(), "Server Configuration is needed", Toast.LENGTH_SHORT).show();
        }

        else{
            String ProxyURL = getServerURL("ProxyURL");
            String ProxyPort = getServerURL("ProxyPort");
            ProxyURL = ProxyURL + ":" + ProxyPort + "/dash_lifemedia/lifemedia-monitoring/MonitorServlet?";
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
            GetContentsList getList = new GetContentsList(ProxyURL, "getCompletedContents");
            getList.start();

            try {
                getList.join();
            }

            catch (InterruptedException e) {
                e.printStackTrace();
            }

            sampleListFromServer = getList.getSamples();

            if(sampleListFromServer.isEmpty()){
                Toast.makeText(getApplicationContext(), "Server is terminated", Toast.LENGTH_LONG).show();
            }

            else{
                Toast.makeText(getApplicationContext(), "List Refreshing", Toast.LENGTH_SHORT).show();
            }

            sampleAdapter.clear();
            sampleAdapter.add(new Header("LifeMedia Contents"));

            for(int index = 0; index < sampleListFromServer.size(); index++){
                sampleAdapter.add(sampleListFromServer.get(index));
            }

            sampleAdapter.notifyDataSetChanged();
        }
    }

    public void serverSettings() {
        Log.d(TAG_SAMPLE,"serverSettings");
        AlertDialog.Builder AddURLBuilder = new AlertDialog.Builder(SampleChooserActivity.this);

        AddURLBuilder.setCancelable(false);

        LayoutInflater inflater = getLayoutInflater();
        final View AddURLView = inflater.inflate(R.layout.server_url_dialog, null);

        AddURLBuilder.setView(AddURLView);

        final EditText ServerURL1 = (EditText) AddURLView.findViewById(R.id.EditServerURL1);
        final EditText ServerURL2 = (EditText) AddURLView.findViewById(R.id.EditServerURL2);
        final EditText ServerURL3 = (EditText) AddURLView.findViewById(R.id.EditServerURL3);
        final EditText ServerURL4 = (EditText) AddURLView.findViewById(R.id.EditServerURL4);
        final EditText ServerPath = (EditText) AddURLView.findViewById(R.id.EditServerPath);

        final EditText ProxyURL1 = (EditText) AddURLView.findViewById(R.id.EditProxyURL1);
        final EditText ProxyURL2 = (EditText) AddURLView.findViewById(R.id.EditProxyURL2);
        final EditText ProxyURL3 = (EditText) AddURLView.findViewById(R.id.EditProxyURL3);
        final EditText ProxyURL4 = (EditText) AddURLView.findViewById(R.id.EditProxyURL4);
        final EditText ProxyPort = (EditText) AddURLView.findViewById(R.id.EditProxyPort);
        final EditText ProxyPath = (EditText) AddURLView.findViewById(R.id.EditProxyPath);

        if (!getServerURL("ServerURL").equals(null) && !getServerURL("ServerURL").equals("")) {
            String[] split = getServerURL("ServerURL").split("http://");
            String[] dotSplit = split[1].toString().split("\\.");
            ServerURL1.setText(dotSplit[0]);
            ServerURL2.setText(dotSplit[1]);
            ServerURL3.setText(dotSplit[2]);
            ServerURL4.setText(dotSplit[3]);
        }

        else {
            ServerURL1.setHint("111");
            ServerURL2.setHint("111");
            ServerURL3.setHint("111");
            ServerURL4.setHint("111");
        }

        if (!getServerURL("ServerPath").equals(null) && !getServerURL("ServerPath").equals("")) {
            ServerPath.setText(getServerURL("ServerPath"));
        }

        else {
            ServerPath.setHint("ContentPath");
        }

        if (!getServerURL("ProxyURL").equals(null) && !getServerURL("ProxyURL").equals("")) {
            String[] split = getServerURL("ProxyURL").split("http://");
            String[] dotSplit = split[1].toString().split("\\.");
            ProxyURL1.setText(dotSplit[0]);
            ProxyURL2.setText(dotSplit[1]);
            ProxyURL3.setText(dotSplit[2]);
            ProxyURL4.setText(dotSplit[3]);
        }

        else {
            ProxyURL1.setHint("111");
            ProxyURL2.setHint("111");
            ProxyURL3.setHint("111");
            ProxyURL4.setHint("111");
        }

        if (!getServerURL("ProxyPort").equals(null) && !getServerURL("ProxyPort").equals("")) {
            if (!ProxyPort.equals(null))
                ProxyPort.setText(getServerURL("ProxyPort"));
        }

        else {
            ProxyPort.setHint("8080");
        }

        if (!getServerURL("ProxyPath").equals(null) && !getServerURL("ProxyPath").equals("")) {
            ProxyPath.setText(getServerURL("ProxyPath"));
        }

        else {
            ProxyPath.setHint("ContentPath");
        }

        AddURLBuilder.setPositiveButton("Configuration", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String Server_URL = "http://" + ServerURL1.getText().toString() + "." + ServerURL2.getText().toString()
                        + "." + ServerURL3.getText().toString() + "." + ServerURL4.getText().toString();
                String Server_Path = ServerPath.getText().toString();
                String Proxy_URL = "http://" + ProxyURL1.getText().toString() + "." + ProxyURL2.getText().toString()
                        + "." + ProxyURL3.getText().toString() + "." + ProxyURL4.getText().toString();
                String Proxy_Port = ProxyPort.getText().toString();
                String Proxy_Path = ProxyPath.getText().toString();

                setServerURL("ServerURL", Server_URL);
                setServerURL("ServerPath", Server_Path);
                setServerURL("ProxyURL", Proxy_URL);
                setServerURL("ProxyPort", Proxy_Port);
                setServerURL("ProxyPath", Proxy_Path);

                try {
                    refreshContentsList(sampleAdapter);
                }

                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        AddURLBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        final AlertDialog AddURLDialog = AddURLBuilder.create();

        TextWatcher textWatcher = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final Button Positive = AddURLDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (s.length() >= 1) {
                    Positive.setEnabled(true);
                }

                else {
                    Positive.setEnabled(false);
                }
            }
        };

        ServerURL1.addTextChangedListener(textWatcher);
        ServerURL2.addTextChangedListener(textWatcher);
        ServerURL3.addTextChangedListener(textWatcher);
        ServerURL4.addTextChangedListener(textWatcher);
        ProxyURL1.addTextChangedListener(textWatcher);
        ProxyURL2.addTextChangedListener(textWatcher);
        ProxyURL3.addTextChangedListener(textWatcher);
        ProxyURL4.addTextChangedListener(textWatcher);
        ProxyPort.addTextChangedListener(textWatcher);
        ServerPath.addTextChangedListener(textWatcher);
        ProxyPath.addTextChangedListener(textWatcher);

        AddURLDialog.show();
        AddURLDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private void setServerURL(String key, String value) {
        Log.d(TAG_SAMPLE,"setServerURL");
        SharedPreferences sp = getSharedPreferences("ServerURL", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(key, value);
        editor.commit();
    }

    private String getServerURL(String key) {
        Log.d(TAG_SAMPLE,"getServerURL");
        SharedPreferences sp = getSharedPreferences("ServerURL", MODE_PRIVATE);
        return sp.getString(key, "");
    }

    private void onSampleSelected(Sample sample) {
        Intent mpdIntent = new Intent(this, PlayerActivity.class).setData(Uri.parse(sample.uri))
                .putExtra(PlayerActivity.CONTENT_ID_EXTRA, sample.contentId)
                .putExtra(PlayerActivity.CONTENT_TYPE_EXTRA, sample.type)
                .putExtra(PlayerActivity.PROVIDER_EXTRA, sample.provider);

        startActivity(mpdIntent);
    }

  private static class Header {

    public final String name;

    public Header(String name) {
      this.name = name;
    }

  }

}
