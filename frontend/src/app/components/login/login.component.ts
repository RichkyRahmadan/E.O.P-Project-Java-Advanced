import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  credentials = {
    username: '',
    password: ''
  };

  errorMessage: string | null = null;
  loading = false;

  onSubmit(): void {
    if (!this.credentials.username || !this.credentials.password) {
      this.errorMessage = 'Username dan Password wajib diisi.';
      return;
    }

    this.loading = true;
    this.errorMessage = null;

    this.authService.login(this.credentials).subscribe({
      next: (res) => {
        const role = this.authService.getUserRole();
        if (role === 'USER') {
          this.router.navigate(['/dashboard/user']);
        } else if (role === 'MERCHANT') {
          this.router.navigate(['/dashboard/merchant']);
        } else if (role === 'ADMIN') {
          this.router.navigate(['/dashboard/admin']);
        } else {
          this.errorMessage = 'Role tidak dikenali.';
          this.authService.clearSession();
        }
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = err.error?.message || 'Login gagal. Silakan periksa kembali username dan password Anda.';
        this.loading = false;
      }
    });
  }
}
