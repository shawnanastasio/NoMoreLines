package io.anastas.nomorelines;

import com.google.api.client.util.Key;

import java.io.Serializable;
import java.util.List;

class PopularTime implements Serializable {
    @Key
    String name;

    @Key
    List<Integer> data;
}

public class PopularTimes implements Serializable {
    @Key
    List<PopularTime> populartimes;
}
