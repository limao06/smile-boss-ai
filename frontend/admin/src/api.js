import axios from 'axios'
const api = axios.create({ baseURL: '/api', timeout: 120000 })
api.interceptors.request.use(config => { const token = localStorage.getItem('smile_admin_token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config })
api.interceptors.response.use(r => { if (r.data?.code !== 0) return Promise.reject(new Error(r.data?.message || '请求失败')); return r.data.data }, e => Promise.reject(new Error(e.response?.data?.message || e.message)))
export default api

