import axios from 'axios'
const api=axios.create({baseURL:'/api',timeout:120000})
api.interceptors.request.use(c=>{const t=localStorage.getItem('smile_candidate_token');if(t)c.headers.Authorization=`Bearer ${t}`;return c})
api.interceptors.response.use(r=>r.config.responseType==='blob'?r.data:(r.data?.code===0?r.data.data:Promise.reject(new Error(r.data?.message||'请求失败'))),e=>Promise.reject(new Error(e.response?.data?.message||e.message)))
export default api
