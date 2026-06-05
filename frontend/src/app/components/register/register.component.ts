import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  userData = {
    username: '',
    email: '',
    password: ''
  };

  errorMessage: string | null = null;
  loading = false;

  onSubmit(): void {
    if (!this.userData.username || !this.userData.email || !this.userData.password) {
      this.errorMessage = 'Semua field wajib diisi.';
      return;
    }

    if (this.userData.password.length < 8) {
      this.errorMessage = 'Password minimal terdiri dari 8 karakter.';
      return;
    }

    this.loading = true;
    this.errorMessage = null;

    this.authService.registerUser(this.userData).subscribe({
      next: (res) => {
        this.loading = false;
        // Upon successful registration, the API automatically logs in and returns tokens.
        // Direct users to dashboard. (It will be PENDING status until verified by Admin)
        this.router.navigate(['/dashboard/user']);
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = err.error?.message || 'Registrasi gagal. Silakan coba username atau email lain.';
        this.loading = false;
      }
    });
  }
}
