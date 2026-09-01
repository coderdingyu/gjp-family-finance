import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表。除登录页外全部挂在 MainLayout 下，共用左侧菜单和顶栏。
 * 登录态用 sessionStorage 里的一份副本做前置判断，真正的鉴权在后端拦截器。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('../views/home/HomeView.vue'),
        meta: { title: '家庭看板' }
      },
      {
        path: 'record',
        name: 'record',
        component: () => import('../views/record/RecordView.vue'),
        meta: { title: '收支流水' }
      },
      {
        path: 'stat',
        name: 'stat',
        component: () => import('../views/stat/StatView.vue'),
        meta: { title: '统计报表' }
      },
      {
        path: 'analysis',
        name: 'analysis',
        component: () => import('../views/analysis/AnalysisView.vue'),
        meta: { title: '智能分析' }
      },
      {
        path: 'member',
        name: 'member',
        component: () => import('../views/member/MemberView.vue'),
        meta: { title: '成员管理' }
      },
      {
        path: 'category',
        name: 'category',
        component: () => import('../views/category/CategoryView.vue'),
        meta: { title: '分类管理' }
      },
      {
        path: 'asset',
        name: 'asset',
        component: () => import('../views/asset/AssetView.vue'),
        meta: { title: '资产负债' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 管家婆` : '管家婆'
  const logged = !!sessionStorage.getItem('gjp_user')
  if (!to.meta.public && !logged) {
    next('/login')
  } else if (to.meta.public && logged) {
    next('/home')
  } else {
    next()
  }
})

export default router
