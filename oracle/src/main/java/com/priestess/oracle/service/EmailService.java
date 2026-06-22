package com.priestess.oracle.service;

import com.priestess.oracle.entity.ComplaintDocument;

public interface EmailService {

    void sendHighPriorityAlert(ComplaintDocument complaint);
}
