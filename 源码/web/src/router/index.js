import { createRouter, createWebHistory } from 'vue-router'
import { currentUser, ROLE } from '../utils/auth'
import { homeOf, resetTabIdentity, verifyCurrentUser } from '../utils/authSession'

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

router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title} - 管家婆` : '管家婆'

  // 「新标签页登录其他账号」打开的页面。浏览器在某些情况下会把来源标签页的
  // sessionStorage 复制过来（手动复制标签页也会），这里把带过来的身份丢掉，
  // 否则新标签页打开就直接是同一个账号，登不了第二个号。
  //
  // 注意只清本地，绝不能调后端退出接口：这个 token 属于来源标签页，
  // 退掉会把还在正常使用的那个标签页一起踢下线。
  if (to.path === '/login' && to.query.fresh !== undefined) {
    resetTabIdentity()
    // 去掉 query，避免刷新时反复触发清理
    return { path: '/login', replace: true }
  }

  // 首次加载、刷新和直接打开新标签页时，都先向服务端确认本标签页的真实身份。
  // 网络暂时不可用时保留本地展示缓存；任何后续业务请求仍会由服务端权限兜底。
  try {
    await verifyCurrentUser()
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
