import { createRouter, createWebHistory } from 'vue-router'
import { currentUser, ROLE } from '../utils/auth'
import { homeOf, verifyCurrentUser } from '../utils/authSession'

/**
 * 路由表。
 *
 * meta.roles 声明允许访问该页面的角色，路由守卫据此拦截：
 *   · 系统管理员登录后只能进 /admin/*，进不了记账相关页面
 *   · 普通成员进不了资产负债、账号管理操作和管理员界面
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
        path: 'import',
        name: 'import',
        component: () => import('../views/import/ImportView.vue'),
        meta: { title: '文件导入', roles: [ROLE.MEMBER, ROLE.OWNER] }
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
        meta: { title: '成员与账号', roles: [ROLE.MEMBER, ROLE.OWNER] }
      },
      {
        path: 'accounts',
        redirect: '/member'
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

router.beforeEach(async (to, from) => {
  document.title = to.meta.title ? `${to.meta.title} - 管家婆` : '管家婆'

  // 首次加载、刷新和直接打开新标签页时，都先向服务端确认真实身份。
  // 网络暂时不可用时保留本地展示缓存；任何后续业务请求仍会由服务端权限兜底。
  try {
    const { user, changed } = await verifyCurrentUser({ broadcastExpiry: true })
    if (changed && user && from.matched.length) {
      // 已挂载页面发现身份变化时，必须销毁旧账号的组件状态和未完成请求。
      window.location.replace(homeOf(user.role))
      return false
    }
  } catch (e) {
    // 非 401（例如服务未启动）由请求层提示，此处不把网络故障误判为退出登录。
  }
  const user = currentUser.value
  const logged = !!user.userId
  const role = user.role ?? ROLE.MEMBER

  if (!to.meta.public && !logged) {
    return '/login'
  }
  if (to.meta.public && logged) {
    return homeOf(role)
  }
  if (to.meta.roles && !to.meta.roles.includes(role)) {
    // 无权访问就送回该角色的首页，而不是停在一个空白页
    return homeOf(role)
  }
  return true
})

export default router
