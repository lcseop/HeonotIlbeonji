import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: '헌옷일번지 | 헌옷 방문수거',
  description: '일산 헌옷 방문수거. 현장에서 확인하고 정산해 드립니다.',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
