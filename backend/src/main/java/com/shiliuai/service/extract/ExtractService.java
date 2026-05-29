package com.shiliuai.service.extract;

import com.shiliuai.dto.ExtractRequest;
import com.shiliuai.dto.ExtractResult;

public interface ExtractService {
    ExtractResult extract(ExtractRequest request);
}
