# 理财投资决策系统 - 微信小程序

## 项目概览

这是一个基于微信小程序原生开发的理财投资决策系统，包含以下核心功能模块：

- **首页**：财务概览、基金/股票TOP3、市场提示
- **股票筛选**：实时股票列表、分页加载、下拉刷新
- **基金筛选**：分类筛选、智能排行、分页加载
- **个人中心**：登录登出、缓存管理、系统信息

## 快速开始

### 1. 安装并启动模拟服务器

```bash
cd wechat-miniprogram/mock-server
npm install
npm run dev
```

模拟服务器将在 `http://localhost:3001` 运行。

### 2. 导入微信开发者工具

1. 打开微信开发者工具
2. 选择「导入项目」
3. 项目目录选择：`wechat-miniprogram`
4. AppID 选择「测试号」或使用自己的AppID
5. 点击「确定」

### 3. 预览和调试

导入成功后，你将看到四个主Tab页：
- 首页
- 股票
- 基金
- 我的

所有数据均来自本地模拟服务器，标记有「模拟数据」标签。

## API接口文档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/home/overview | 首页概览数据 |
| GET | /api/stock/list?page=1&size=10 | 股票列表(分页) |
| GET | /api/fund/list?type=全部&page=1&size=10 | 基金列表(分类+分页) |
| POST | /api/auth/login | 用户登录 |

## 项目结构

```
wechat-miniprogram/
├── images/             # 图标资源
├── config/             # 配置文件
│   └── api.config.js   # API基础配置
├── utils/              # 工具函数
│   └── request.js      # 网络请求封装
├── pages/              # 页面目录
│   ├── index/          # 首页
│   ├── stock/          # 股票列表
│   ├── fund/           # 基金列表
│   └── profile/        # 个人中心
├── app.js              # 小程序入口
├── app.json            # 全局配置
├── app.wxss            # 全局样式
└── project.config.json # 项目配置
```

## 模拟服务器

模拟服务器提供与真实后端API一致的响应格式：

```json
{
    "code": 0,
    "message": "success",
    "data": { ... }
}
```

支持的功能：
- faker生成中文模拟数据
- 完整的分页支持
- 基金分类筛选
- TabBar图标资源

## 开发注意事项

1. 模拟数据通过 `_mock: true` 字段标识
2. 切换环境只需修改 `config/api.config.js` 中的baseURL
3. 所有页面支持下拉刷新和上拉加载更多
4. 网络连接失败时会显示Toast提示

## License

MIT