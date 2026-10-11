import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import { Loading } from './components/ui.jsx';
import Login from './pages/Login.jsx';
import Home from './pages/Home.jsx';
import StorePage from './pages/StorePage.jsx';
import ProductPage from './pages/ProductPage.jsx';
import Cart from './pages/Cart.jsx';
import MyOrders from './pages/MyOrders.jsx';
import SellerHome from './pages/seller/SellerHome.jsx';
import StoreForm from './pages/seller/StoreForm.jsx';
import StoreDashboard from './pages/seller/StoreDashboard.jsx';
import ScanNew from './pages/seller/ScanNew.jsx';
import ScanJob from './pages/seller/ScanJob.jsx';
import ImportNew from './pages/seller/ImportNew.jsx';
import ImportBatch from './pages/seller/ImportBatch.jsx';
import ProductForm from './pages/seller/ProductForm.jsx';
import Metrics from './pages/admin/Metrics.jsx';
import './style.css';

/** 로그인이 필요한 화면. 로그인하지 않았으면 로그인 화면으로 보내고, 끝나면 돌아온다. */
function RequireLogin({ children, admin = false }) {
  const { me, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Loading />;
  if (!me) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  if (admin && me.role !== 'ADMIN') return <p className="alert">관리자만 볼 수 있습니다.</p>;
  return children;
}

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* 구매자 */}
        <Route index element={<Home />} />
        <Route path="login" element={<Login />} />
        <Route path="s/:slug" element={<StorePage />} />
        <Route path="p/:productId" element={<ProductPage />} />
        <Route path="cart" element={<Cart />} />
        <Route path="orders" element={<RequireLogin><MyOrders /></RequireLogin>} />
        {/* 판매자 */}
        <Route path="seller" element={<RequireLogin><SellerHome /></RequireLogin>} />
        <Route path="seller/stores/new" element={<RequireLogin><StoreForm /></RequireLogin>} />
        <Route path="seller/stores/:storeId" element={<RequireLogin><StoreDashboard /></RequireLogin>} />
        <Route path="seller/stores/:storeId/scan" element={<RequireLogin><ScanNew /></RequireLogin>} />
        <Route path="seller/stores/:storeId/import" element={<RequireLogin><ImportNew /></RequireLogin>} />
        <Route path="seller/import-batches/:batchId" element={<RequireLogin><ImportBatch /></RequireLogin>} />
        <Route path="seller/stores/:storeId/products/new" element={<RequireLogin><ProductForm /></RequireLogin>} />
        <Route path="seller/products/:productId/edit" element={<RequireLogin><ProductForm /></RequireLogin>} />
        <Route path="seller/scan-jobs/:jobId" element={<RequireLogin><ScanJob /></RequireLogin>} />
        {/* 관리자 */}
        <Route path="admin" element={<RequireLogin admin><Metrics /></RequireLogin>} />
        <Route path="*" element={<p className="alert">없는 화면입니다.</p>} />
      </Route>
    </Routes>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
