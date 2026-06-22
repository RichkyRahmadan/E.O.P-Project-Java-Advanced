import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SupportService, ComplaintDocument } from '../../services/support.service';

@Component({
  selector: 'app-support-complaints',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './support-complaints.component.html',
  styleUrls: ['./support-complaints.component.css']
})
export class SupportComplaintsComponent implements OnInit {
  private authService = inject(AuthService);
  private supportService = inject(SupportService);
  private router = inject(Router);

  userRole = '';
  userId = '';

  // Form
  complaintForm = {
    invoiceId: '',
    rawMessage: ''
  };

  // Lists
  myComplaints: ComplaintDocument[] = [];
  adminComplaints: ComplaintDocument[] = [];
  adminFilterStatus = 'OPEN';

  // UI state variables
  submitSuccess: string | null = null;
  submitError: string | null = null;
  loading = false;
  adminLoading = false;
  selectedComplaint: ComplaintDocument | null = null;

  ngOnInit(): void {
    this.userRole = this.authService.getUserRole() || '';
    this.userId = this.authService.getUserId() || '';

    if (this.userRole === 'ADMIN') {
      this.loadAdminComplaints();
    } else {
      this.loadMyComplaints();
    }
  }

  loadMyComplaints(): void {
    this.supportService.getMyComplaints().subscribe({
      next: (res) => {
        this.myComplaints = res.data;
      },
      error: (err) => {
        console.error('Gagal mengambil keluhan saya', err);
      }
    });
  }

  loadAdminComplaints(): void {
    this.adminLoading = true;
    this.supportService.getComplaintsByStatus(this.adminFilterStatus).subscribe({
      next: (res) => {
        this.adminComplaints = res.data;
        this.adminLoading = false;
      },
      error: (err) => {
        console.error('Gagal mengambil keluhan untuk admin', err);
        this.adminLoading = false;
      }
    });
  }

  onSubmitComplaint(): void {
    if (!this.complaintForm.rawMessage.trim()) {
      this.submitError = 'Isi keluhan tidak boleh kosong.';
      return;
    }

    this.loading = true;
    this.submitError = null;
    this.submitSuccess = null;

    this.supportService.submitComplaint(
      this.complaintForm.invoiceId.trim(),
      this.complaintForm.rawMessage.trim()
    ).subscribe({
      next: (res) => {
        this.submitSuccess = res.message || 'Keluhan berhasil dikirim. Fitur analisis Gemini AI masih dalam pengembangan.';
        this.complaintForm = { invoiceId: '', rawMessage: '' };
        this.loading = false;
        // Refresh list
        this.loadMyComplaints();
      },
      error: (err) => {
        console.error(err);
        this.submitError = err.error?.message || 'Gagal mengirim keluhan. Coba lagi beberapa saat.';
        this.loading = false;
      }
    });
  }

  onFilterStatusChange(status: string): void {
    this.adminFilterStatus = status;
    this.loadAdminComplaints();
  }

  selectComplaint(complaint: ComplaintDocument): void {
    this.selectedComplaint = complaint;
  }

  closeModal(): void {
    this.selectedComplaint = null;
  }

  onUpdateStatus(complaintId: string, status: string): void {
    this.supportService.updateComplaintStatus(complaintId, status).subscribe({
      next: (res) => {
        // Refresh the selected details and the admin list
        if (this.selectedComplaint && this.selectedComplaint.complaintId === complaintId) {
          this.selectedComplaint = res.data;
        }
        this.loadAdminComplaints();
      },
      error: (err) => {
        console.error('Failed to update status', err);
        alert('Gagal mengubah status keluhan.');
      }
    });
  }

  backToDashboard(): void {
    if (this.userRole === 'USER') {
      this.router.navigate(['/dashboard/user']);
    } else if (this.userRole === 'MERCHANT') {
      this.router.navigate(['/dashboard/merchant']);
    } else if (this.userRole === 'ADMIN') {
      this.router.navigate(['/dashboard/admin']);
    } else {
      this.router.navigate(['/login']);
    }
  }
}
