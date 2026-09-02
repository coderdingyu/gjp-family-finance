import { createRouter, createWebHistory } from 'vue-router'
import { ROLE } from '../utils/auth'

/**
 * 路由表。
 *
 * meta.roles 声明允许访问该页面的角色，路由守卫据此拦截：
 *   · 系统管理员登录后只能进 /admin/*，进不了记账相关页面
 *   · 普通成员进不了资产负债、成员账号、管理员界面
 * 这只是"不让用户看到无权页面"的体验优化，真正的权限在后端每个接口里。
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
        meta: { title: '家庭看板', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'record',
        name: 'record',
        component: () => import('../views/record/RecordView.vue'),
        meta: { title: '收支流水', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'stat',
        name: 'stat',
        component: () => import('../views/stat/StatView.vue'),
        meta: { title: '统计报表', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'analysis',
        name: 'analysis',
        component: () => import('../views/analysis/AnalysisView.vue'),
        meta: { title: '智能分析', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'dedup',
        name: 'dedup',
        component: () => import('../views/dedup/DedupView.vue'),
        meta: { title: '账单查重', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'asset',
        name: 'asset',
        component: () => import('../views/asset/AssetView.vue'),
        meta: { title: '资产负债', roles: [ROLE.OWNER] }
      },
      {
        path: 'member',
        name: 'member',
        component: () => import('../views/member/MemberView.vue'),
        meta: { title: '成员管理', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'accounts',
        name: 'accounts',
        component: () => import('../views/member/AccountsView.vue'),
        meta: { title: '成员账号', roles: [ROLE.OWNER] }
      },
      {
        path: 'category',
        name: 'category',
        component: () => import('../views/category/CategoryView.vue'),
        meta: { title: '分类管理', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'log',
        name: 'log',
        component: () => import('../views/log/LogView.vue'),
        meta: { title: '操作日志', roles: [ROLE.MEMBER, ROLE.OWNER, ROLE.ADMIN] }
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('../views/admin/AdminView.vue'),
        meta: { title: '系统维护', roles: [ROLE.ADMIN] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 各角色登录后的落地页 */
function homeOf(role) {
  return role === ROLE.ADMIN ? '/admin' : '/home'
}

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 管家婆` : '管家婆'

  // 守卫里直接读 sessionStorage 而不是读 auth.js 的 ref：
  // 刷新页面时守卫先于组件执行，此刻 ref 也是从 sessionStorage 初始化的，二者等价；
  // 但直连某个 URL 时读存储更直观，不依赖模块初始化顺序。
  const stored = JSON.parse(sessionStorage.getItem('gjp_user') || '{}')
  const logged = !!stored.userId
  const role = stored.role ?? ROLE.MEMBER

  if (!to.meta.public && !logged) {
    return next('/login')
  }
  if (to.meta.public && logged) {
    return next(homeOf(role))
  }
  if (to.meta.roles && !to.meta.roles.includes(role)) {
    // 无权访问就送回该角色的首页，而不是停在一个空白页
    return next(homeOf(role))
  }
  next()
})

export default router
