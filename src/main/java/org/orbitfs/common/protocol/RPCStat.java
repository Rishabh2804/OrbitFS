package org.orbitfs.common.protocol;

import java.io.Serializable;
import java.time.Instant;

public class RPCStat implements Serializable {
    private int size;
    private boolean isDir;
    private Instant timestamp;
}
