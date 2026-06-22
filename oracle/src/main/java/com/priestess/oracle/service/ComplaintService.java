package com.priestess.oracle.service;

import com.priestess.oracle.entity.ComplaintDocument;

import java.util.List;

public interface ComplaintService {

    ComplaintDocument getComplaintById(String complaintId);

    List<ComplaintDocument> getMyComplaints(String userId);

    List<ComplaintDocument> getComplaintsByStatus(String status);

    ComplaintDocument updateStatus(String complaintId, String newStatus);
}
