import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T;
}

export interface AiAnalysis {
  category: string;
  priority: string;
  sentiment: string;
  score: number;
  suggestedReply: string;
}

export interface ComplaintDocument {
  complaintId: string;
  userId: string;
  username: string;
  email: string;
  invoiceId: string;
  rawMessage: string;
  status: string;
  aiAnalysis?: AiAnalysis;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class SupportService {
  private http = inject(HttpClient);

  submitComplaint(invoiceId: string, rawMessage: string): Observable<ApiResponse<ComplaintDocument>> {
    return this.http.post<ApiResponse<ComplaintDocument>>('/api/support/complaints', {
      invoiceId,
      rawMessage
    });
  }

  getMyComplaints(): Observable<ApiResponse<ComplaintDocument[]>> {
    return this.http.get<ApiResponse<ComplaintDocument[]>>('/api/support/complaints/my');
  }

  getComplaintById(complaintId: string): Observable<ApiResponse<ComplaintDocument>> {
    return this.http.get<ApiResponse<ComplaintDocument>>(`/api/support/complaints/${complaintId}`);
  }

  getComplaintsByStatus(status: string): Observable<ApiResponse<ComplaintDocument[]>> {
    return this.http.get<ApiResponse<ComplaintDocument[]>>(`/api/support/complaints/status/${status}`);
  }

  updateComplaintStatus(complaintId: string, newStatus: string): Observable<ApiResponse<ComplaintDocument>> {
    return this.http.patch<ApiResponse<ComplaintDocument>>(`/api/support/complaints/${complaintId}/status?newStatus=${newStatus}`, null);
  }
}
