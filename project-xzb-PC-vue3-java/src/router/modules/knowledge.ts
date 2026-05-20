import Layout from '@/layouts/index.vue'

const knowledgeRouter = [
  {
    path: '/knowledge',
    name: 'knowledge',
    component: Layout,
    redirect: '/knowledge/index',
    meta: {
      title: '知识库管理',
      icon: {
        render: () => '📚'
      },
      single: true,
    },
    children: [
      {
        path: 'index',
        name: 'knowledgeIndex',
        component: () => import('@/pages/knowledge/index.vue'),
        meta: {
          title: '知识库管理',
          singles: true,
        },
      },
    ],
  },
]

export default knowledgeRouter
