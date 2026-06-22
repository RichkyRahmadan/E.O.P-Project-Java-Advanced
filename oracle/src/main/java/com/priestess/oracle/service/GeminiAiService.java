package com.priestess.oracle.service;

import com.priestess.oracle.entity.ComplaintDocument;

public interface GeminiAiService {

    void analyzeComplaint(ComplaintDocument complaint);
}
