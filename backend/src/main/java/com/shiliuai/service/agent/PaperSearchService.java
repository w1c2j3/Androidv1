package com.shiliuai.service.agent;

import com.shiliuai.dto.PaperDto;

import java.util.List;

public interface PaperSearchService {
    List<PaperDto> search(String command, int limit);
}
