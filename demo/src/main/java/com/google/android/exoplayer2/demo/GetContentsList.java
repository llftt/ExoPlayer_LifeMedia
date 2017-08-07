package com.google.android.exoplayer2.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.demo.Samples.Sample;

/**
 * Created by cclab on 2017-04-21.
 */

public class GetContentsList extends Thread{

    List<Sample> samplesFromServer = new ArrayList<Sample>();
    String url;
    String data;

    public GetContentsList(String url, String data) {
        samplesFromServer = new ArrayList<Sample>();
        this.url = url;
        this.data = data;
    }

    public List<Sample> getSamples(){
        return this.samplesFromServer;
    }

    public void run() {
        HttpURLConnection urlConnection = null;

        try {
            try {
                urlConnection = (HttpURLConnection) new URL(url).openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(data != null);
                urlConnection.setDoInput(true);
                urlConnection.setConnectTimeout(1000);
                urlConnection.setReadTimeout(5000);

                if (data != null) {
                    OutputStream outputStream = urlConnection.getOutputStream();

                    try {
                        outputStream.write(data.getBytes());
                    }

                    finally {
                        outputStream.flush();
                        outputStream.close();
                    }
                }

                int responseCode = urlConnection.getResponseCode();
                InputStream inputStream = urlConnection.getInputStream();
                List<Sample> returnSamples = new ArrayList<Sample>();

                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
                    String line = reader.readLine();

                    if(line != null){
                        String outData = URLDecoder.decode(line, "UTF-8");

                        if (!outData.equals("") && outData != null) {
                            String[] contentsInfo = outData.split("\\@");

                            for (int index = 0; index < contentsInfo.length; index++) {
                                String[] contentInfo = contentsInfo[index].split("\\|");

                                Sample contentsSample = new Samples.Sample(contentInfo[0], contentInfo[1], C.TYPE_DASH);
                                returnSamples.add(contentsSample);
                            }

                            this.samplesFromServer = returnSamples;
                        }
                    }
                }

                inputStream.close();
            }

            catch (MalformedURLException e) {
                e.printStackTrace();
            }

            catch (IOException e) {
                e.printStackTrace();
            }
        }

        finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }
    }

}
