package com.google.android.exoplayer2.demo;

import java.util.Locale;

/**
 * Created by cclab on 2017-04-21.
 */

public class Samples {

    public static class Sample {

        public final String name;
        public final String contentId;
        public final String provider;
        public final String uri;
        public final int type;

        public Sample(String name, String uri, int type) {
            this(name, name.toLowerCase(Locale.US).replaceAll("\\s", ""), "", uri, type);
        }

        public Sample(String name, String contentId, String provider, String uri, int type) {
            this.name = name;
            this.contentId = contentId;
            this.provider = provider;
            this.uri = uri;
            this.type = type;
        }

    }

    public final static Sample[] LIFE_MEDIA = new Sample[]{
    };

    private Samples() {}

}
