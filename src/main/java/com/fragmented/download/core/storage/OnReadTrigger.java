package com.fragmented.download.core.storage;

@FunctionalInterface
public interface OnReadTrigger {
    void triggerDownload(long offset, int length);
}
