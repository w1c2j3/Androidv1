package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class QueueStatusResponse {
    public boolean healthy;
    public String status;
    public String message;
    public List<QueuePoolStatusDto> pools = new ArrayList<>();
}
