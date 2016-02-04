package com.google.android.exoplayer.metadata;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class Id3Tag implements Map<String, Object> {

    private final Map<String, Object> frames;
    private Set<TxxxMetadata> txxxFrames;

    public Id3Tag() {
        frames = new HashMap<>();
        txxxFrames = new HashSet<>();
    }

    @Override
    public void clear() {
        frames.clear();
    }

    @Override
    public boolean containsKey( Object key ) {
        return frames.containsKey( key );
    }

    @Override
    public boolean containsValue( Object value ) {
        return frames.containsValue( value );
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return frames.entrySet();
    }

    @Override
    public boolean equals( Object object ) {
        return frames.equals( object );
    }

    @Override
    public Object get( Object key ) {
        return frames.get( key );
    }

    @Override
    public int hashCode() {
        return frames.hashCode();
    }

    @Override
    public boolean isEmpty() {
        return frames.isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return frames.keySet();
    }

    @Override
    public Object put( String key, Object value ) {
        return frames.put( key, value );
    }

    @Override
    public void putAll( Map<? extends String, ?> map ) {
        frames.putAll( map );
    }

    @Override
    public Object remove( Object key ) {
        return frames.remove( key );
    }

    @Override
    public int size() {
        return frames.size();
    }

    @Override
    public Collection<Object> values() {
        return frames.values();
    }

    public void addTxxxFrame( TxxxMetadata txxxMetadata ) {
        txxxFrames.add( txxxMetadata );
    }

    public Set<TxxxMetadata> getTxxxFrames() {
        return txxxFrames;
    }
}
