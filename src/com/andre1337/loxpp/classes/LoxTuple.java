package com.andre1337.loxpp.classes;

import java.util.List;

public record LoxTuple(List<Object> elements) {
    public Object get(int index) {
        return elements.get(index);
    }

    public int size() {
        return elements.size();
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}
