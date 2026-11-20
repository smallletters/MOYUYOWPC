/* ============================================================
 * MOYUYO ATELIER · 可交互原型 mock 数据
 * 传统脚本（非 ES module），file:// 直接可用
 * ============================================================ */
(function (w) {
  'use strict';

  /* ---------- 四产品线色板（01 §2.1.5） ---------- */
  var LINE = {
    CARE: { c1: '#DBC98A', c2: '#B39F66', icon: 'package', label: 'CARE', ink: '#6E5C30' },
    GEAR: { c1: '#ABB9AD', c2: '#7F8F83', icon: 'shopping-bag', label: 'GEAR', ink: '#5F7259' },
    PLAY: { c1: '#B38A5A', c2: '#8A6540', icon: 'shirt', label: 'PLAY', ink: '#6E5C30' },
    HOME: { c1: '#D9B4B0', c2: '#B98C88', icon: 'home', label: 'HOME', ink: '#8C5F5A' }
  };

  function goods(name, desc, price, orig, rating, sold, line, img) {
    var l = LINE[line];
    return {
      name: name, desc: desc, price: price, orig: orig,
      rating: rating, sold: sold, line: line,
      c1: l.c1, c2: l.c2, icon: l.icon, tag: l.label,
      /* img：真实商品图（assets/photo/）。空串 = 无图，回落色块占位 */
      img: img || ''
    };
  }

  /* ---------- Pet ---------- */
  var pets = [
    {
      id: 'p1', name: 'Luna', breed: 'GOLDEN RETRIEVER', species: '金毛寻回犬',
      age: '3 岁', icon: 'dog', img: 's-dog2.jpg', tint: 'rgba(219,201,138,.26)',
      care: [
        { key: 'wash', icon: 'droplet', label: '洗护', last: '12 天前', status: '状态良好', tone: 'ok' },
        { key: 'vaccine', icon: 'syringe', label: '疫苗', last: '3 天前', status: '即将到期', tone: 'warn' },
        { key: 'deworm', icon: 'pill', label: '驱虫', last: '今天', status: '已到期', tone: 'err' }
      ],
      next: { label: '下次护理', days: '12 天', key: 'wash' }
    },
    {
      id: 'p2', name: 'Momo', breed: 'BRITISH SHORTHAIR', species: '英国短毛猫',
      age: '2 岁', icon: 'cat', img: 's-cat.jpg', tint: 'rgba(171,185,173,.28)',
      care: [
        { key: 'wash', icon: 'droplet', label: '洗护', last: '20 天前', status: '状态良好', tone: 'ok' },
        { key: 'vaccine', icon: 'syringe', label: '疫苗', last: '45 天前', status: '状态良好', tone: 'ok' },
        { key: 'deworm', icon: 'pill', label: '驱虫', last: '5 天前', status: '即将到期', tone: 'warn' }
      ],
      next: { label: '下次护理', days: '18 天', key: 'wash' }
    },
    {
      id: 'p3', name: 'Coco', breed: 'PEMBROKE CORGI', species: '彭布罗克柯基',
      age: '5 岁', icon: 'dog', img: 's-dog.jpg', tint: 'rgba(179,138,90,.28)',
      care: [
        { key: 'wash', icon: 'droplet', label: '洗护', last: '2 天前', status: '即将到期', tone: 'warn' },
        { key: 'vaccine', icon: 'syringe', label: '疫苗', last: '今天', status: '已到期', tone: 'err' },
        { key: 'deworm', icon: 'pill', label: '驱虫', last: '30 天前', status: '状态良好', tone: 'ok' }
      ],
      next: { label: '下次护理', days: '7 天', key: 'deworm' }
    },
    {
      id: 'p4', name: 'Nori', breed: 'RAGDOLL', species: '布偶猫',
      age: '1 岁', icon: 'cat', img: 's-cat2.jpg', tint: 'rgba(217,180,176,.28)',
      care: [
        { key: 'wash', icon: 'droplet', label: '洗护', last: '8 天前', status: '状态良好', tone: 'ok' },
        { key: 'vaccine', icon: 'syringe', label: '疫苗', last: '4 天前', status: '即将到期', tone: 'warn' },
        { key: 'deworm', icon: 'pill', label: '驱虫', last: '15 天前', status: '状态良好', tone: 'ok' }
      ],
      next: { label: '下次护理', days: '21 天', key: 'deworm' }
    }
  ];

  var petQuick = [
    { key: 'mall', icon: 'shopping-bag', label: '商城' },
    { key: 'share', icon: 'camera', label: '晒宠' },
    { key: 'calendar', icon: 'calendar', label: '日历' },
    { key: 'memory', icon: 'book-open', label: '记忆' },
    { key: 'weight', icon: 'scale', label: '体重' }
  ];

  /* ---------- Home ---------- */
  var homeQuick = [
    { key: 'category', icon: 'layout-grid', label: '分类' },
    { key: 'member', icon: 'award', label: '会员' },
    { key: 'coupon', icon: 'ticket', label: '领券' },
    { key: 'flash', icon: 'zap', label: '限时' }
  ];

  var banners = [
    {
      tag: '今日限定', title: '秋日护理礼盒',
      line: 'CARE', icon: 'gift', img: 's-hero.jpg', c1: '#DBC98A', c2: '#A8905A', link: true
    },
    {
      tag: '新品首发', title: '雾灰系列牵引绳',
      line: 'GEAR', icon: 'shopping-bag', img: 's-walk.jpg', c1: '#ABB9AD', c2: '#6E7F73', link: true
    },
    {
      tag: '门店活动', title: '周末宠物友好市集',
      line: 'PLAY', icon: 'party-popper', img: 's-dog2.jpg', c1: '#B38A5A', c2: '#7E5C39', link: false
    }
  ];

  var homeTabs = ['猜你喜欢', '今日爆款', '口碑好评'];

  var products = [
    [
      goods('秋日护理礼盒套装', '洗护 + 护毛 + 梳理三件套', 128, 199, 4.8, '1.2k', 'CARE', 'p-care-shampoo.jpg'),
      goods('雾灰尼龙牵引绳', '反光织带 · 三档长度', 89, 129, 4.7, '860', 'GEAR', 'p-gear-leash.jpg'),
      goods('云朵记忆棉宠物窝', '可拆洗 · S / M / L', 259, 329, 4.9, '2.3k', 'HOME', 'p-home-bed.jpg'),
      goods('羊绒针织小背心', '秋冬保暖 · 六色可选', 168, 219, 4.6, '640', 'PLAY', 'p-play-jacket.jpg'),
      goods('低温烘焙三文鱼粮', '1.5kg · 无谷配方', 189, 239, 4.8, '3.1k', 'CARE', 'p-care-treats.jpg'),
      goods('陶瓷慢食碗', '防噎 · 双碗带架', 76, 98, 4.5, '420', 'GEAR', 'p-home-bowl.png'),
      goods('宠物口腔喷雾', '30ml · 清新口气', 45, 59, 4.4, '1.8k', 'CARE', 'p-care-dental.jpg'),
      goods('编织藤编提篮', '出行 · 透气网面', 218, 268, 4.7, '310', 'HOME', 'p-gear-carrier2.jpg')
    ],
    [
      goods('无谷冻干主粮 2kg', '92% 鲜肉 · 全期适用', 238, 298, 4.9, '5.6k', 'CARE', 'p-care-treats.jpg'),
      goods('三角猫抓板', '高密瓦楞 · 双面可用', 39, 55, 4.6, '2.4k', 'PLAY', 'p-play-scratch.png'),
      goods('可折叠航空箱', '三面通风 · 承重 8kg', 316, 399, 4.7, '720', 'GEAR', 'p-gear-carrier.jpg'),
      goods('智能饮水机 2L', '静音循环 · 可视水位', 149, 189, 4.5, '1.4k', 'GEAR', 'p-gear-fountain.png'),
      goods('乳胶洁齿玩具', '耐咬 · 天然乳胶', 32, 45, 4.4, '3.2k', 'PLAY', 'p-play-toy.jpg'),
      goods('亚麻宠物沙发罩', '防毛 · 可机洗', 128, 168, 4.6, '560', 'HOME', 'p-home-bed.jpg'),
      goods('便携外出水壶', '350ml · 一键出水', 58, 78, 4.8, '1.9k', 'GEAR', 'p-gear-flask.png'),
      goods('宠物湿巾 80 抽', '无酒精 · 温和配方', 26, 35, 4.7, '6.8k', 'CARE', 'p-care-wipes.jpg')
    ],
    [
      goods('氨基酸洗护二合一', '低敏 · 500ml', 98, 128, 4.9, '4.2k', 'CARE', 'p-care-shampoo.jpg'),
      goods('记忆棉护脊床垫', '高回弹 · 防滑底', 189, 249, 4.8, '1.1k', 'HOME', 'p-home-bed.jpg'),
      goods('真皮项圈 LF-01', '植鞣革 · 手工缝制', 158, 198, 4.7, '480', 'GEAR', 'p-gear-leash.jpg'),
      goods('猫用冻干蛋黄粒', '100g · 无添加', 42, 58, 4.9, '7.3k', 'CARE', 'p-care-treats.jpg'),
      goods('冬季绒面雨衣', '防泼水 · 反光条', 138, 178, 4.6, '390', 'PLAY', 'p-play-jacket.jpg'),
      goods('陶瓷储粮罐', '密封 · 附量杯', 68, 88, 4.5, '260', 'HOME', 'p-home-jar.jpg'),
      goods('洁齿磨牙棒 12 支', '天然牛皮 · 低脂', 56, 72, 4.8, '2.7k', 'CARE', 'p-care-biscuits.jpg'),
      goods('折叠便携碗', '硅胶 · 附登山扣', 29, 39, 4.4, '1.5k', 'GEAR', 'p-gear-bowl.png')
    ]
  ];

  /* ---------- Community ---------- */
  var topics = ['#秋日遛狗', '#新手养猫', '#宠物友好餐厅', '#换毛季急救', '#狗狗训练', '#猫咪日常'];

  var communityTabs = ['推荐', '关注', '话题'];

  /* 作者头像实拍映射（无图的作者回落首字占位） */
  var AVA = {
    '拿铁妈': 's-dog2.jpg', '布丁与豆浆': 's-cat.jpg', '南风': 's-dog.jpg',
    '阿黎': 's-cat-sofa.jpg', '小满': 's-cat3.jpg', '橘子汽水': 's-cat2.jpg',
    '老周': 's-bath2.jpg'
  };

  function post(o) {
    return {
      id: o.id, user: o.user, avatar: o.avatar, time: o.time, own: !!o.own,
      text: o.text, likes: o.likes, comments: o.comments,
      // 单图比例：真实产品走 widthFix 跟随原图（01 v1.8 裁决），原型用每张图的原始比例模拟
      ratio: o.ratio || '4x3',
      img: AVA[o.user] || '',
      images: (o.images || []).map(function (it) {
        var l = LINE[it[0]];
        /* it[2] = 该图位的实拍图文件名（assets/photo/），缺省回落色块占位 */
        return { line: it[0], icon: it[1] || l.icon, c1: l.c1, c2: l.c2, tag: l.label, img: it[2] || '' };
      })
    };
  }

  var posts = {
    '0': [
      post({
        id: 'c1', user: '拿铁妈', avatar: '拿', time: '2 小时前',
        text: '秋天的第一场遛弯。拿铁今天在公园里跑了整整四十分钟，回家直接趴在门口睡着了，尾巴还在一抽一抽的。医生说这个季节要多注意关节，于是我把它常走的那条路线从柏油路换成了草地，顺便把牵引绳也换成了更轻的一款。',
        images: [['CARE', 'package', 's-dog.jpg'], ['GEAR', 'shopping-bag', 's-walk.jpg'], ['PLAY', 'shirt', 's-dog2.jpg']],
        likes: 128, comments: 32
      }),
      post({
        id: 'c2', user: '布丁与豆浆', avatar: '布', time: '5 小时前', own: true,
        text: '新手养猫第三周，终于摸清了布丁的作息：早上七点准时来踩脸，晚上十点必须玩二十分钟逗猫棒，否则就在键盘上打滚抗议。前几天它开始对窗外的鸟感兴趣，一坐就是一小时，叫都叫不动，附一张它蹲在窗台的照片。',
        images: [['HOME', 'home', 's-cat.jpg']],
        likes: 86, comments: 19
      }),
      /* 4 图 → 2 列 2 行（裁决 A：2 或 4 图用 2 列），用于验证宫格分支二 */
      post({
        id: 'c13', user: '南风', avatar: '南', time: '6 小时前',
        text: '三个月的用品盘点，从左到右是我会回购的四个：洁齿玩具、饮水机、牵引绳、冻干。踩过的坑下次单独写一篇。玩具选了乳胶的，耐咬也不伤牙；饮水机换成了静音款，晚上终于能睡整觉了。',
        images: [['GEAR', 'brush', 'p-play-toy.jpg'], ['HOME', 'home', 'p-gear-fountain.png'], ['PLAY', 'map-pin', 'p-gear-leash.jpg'], ['CARE', 'bone', 'p-care-treats.jpg']],
        likes: 118, comments: 26
      }),
      post({
        id: 'c3', user: '阿黎', avatar: '黎', time: '昨天',
        text: '分享一家宠物友好餐厅，老板自己养了两只柯基，进门就有给狗狗准备的水碗和垫子。周末去的时候还不算挤，靠窗的位置阳光很好，拍照很出片。菜单上有单独的宠物餐，分量对小型犬刚好，价格也不算贵。',
        images: [['PLAY', 'utensils', 's-cat-sofa.jpg'], ['HOME', 'home', 'p-home-bed.jpg'], ['GEAR', 'map-pin', 's-walk.jpg'], ['CARE', 'bone', 'p-care-treats.jpg'], ['PLAY', 'sparkles', 's-bath.jpg'], ['HOME', 'building-2', 's-cat.jpg']],
        likes: 241, comments: 57
      }),
      post({
        id: 'c4', user: '小满', avatar: '满', time: '昨天',
        text: '换毛季救命帖：梳子从三把精简到一把之后，家里的毛量肉眼可见地下降了。',
        images: [],
        likes: 64, comments: 12
      }),
      post({
        id: 'c5', user: '橘子汽水', avatar: '橘', time: '2 天前', own: true,
        text: '给它做了个生日蛋糕，鸡胸肉泥 + 南瓜 + 一点点酸奶，卖相一般，但它吃得很认真。',
        ratio: '3x2',
        images: [['CARE', 'party-popper', 's-cat2.jpg']],
        likes: 173, comments: 28
      }),
      /* 9 图 → 3 列 3 行，九宫格分支 */
      post({
        id: 'c14', user: '老周', avatar: '周', time: '3 天前',
        text: '周末带两只狗去郊外跑了一整天，回来后它们睡到第二天中午。九张图慢慢看，最后一张是回程车上彻底躺平的样子。这次换的新牵引绳手感很好，长距离走下来手腕也不勒；草地上蚊虫多，建议带上驱虫喷雾。',
        images: [
          ['PLAY', 'sparkles', 's-dog.jpg'], ['GEAR', 'map-pin', 's-walk.jpg'], ['CARE', 'bone', 's-dog2.jpg'],
          ['HOME', 'cloud', 's-golden.jpg'], ['PLAY', 'shirt', 's-cat3.jpg'], ['GEAR', 'shopping-bag', 's-dog.jpg'],
          ['CARE', 'package', 's-bath.jpg'], ['HOME', 'home', 's-bath2.jpg'], ['PLAY', 'utensils', 's-dog2.jpg']
        ],
        likes: 306, comments: 74
      })
    ],
    '1': [
      post({
        id: 'c6', user: '拿铁妈', avatar: '拿', time: '3 小时前',
        text: '约了周六上午一起遛狗，有同城的朋友可以一起来。',
        images: [['GEAR', 'map-pin', 's-walk.jpg']],
        likes: 42, comments: 8
      }),
      post({
        id: 'c7', user: '阿黎', avatar: '黎', time: '8 小时前',
        text: '上次说的那家店我去了第二次，老板还记得我们家狗的名字。',
        images: [['PLAY', 'utensils', 's-cat-sofa.jpg'], ['HOME', 'home', 'p-home-bed.jpg']],
        likes: 96, comments: 14
      }),
      post({
        id: 'c8', user: '小满', avatar: '满', time: '2 天前',
        text: '梳毛日常记录，一周下来攒了一小袋，准备拿去做毛毡。',
        images: [],
        likes: 58, comments: 6
      })
    ],
    '2': [
      post({
        id: 'c9', user: '橘子汽水', avatar: '橘', time: '1 小时前',
        text: '#秋日遛狗 今天风很大，出门前给它套了件薄外套，走到一半就开始打滚，外套全是灰。',
        images: [['PLAY', 'shirt', 'p-play-jacket.jpg'], ['GEAR', 'cloud', 's-walk.jpg']],
        likes: 77, comments: 11
      }),
      post({
        id: 'c10', user: '布丁与豆浆', avatar: '布', time: '4 小时前',
        text: '#新手养猫 求助：布丁最近开始咬数据线，有什么好办法吗？已经换了三根了。',
        images: [],
        likes: 34, comments: 41
      }),
      post({
        id: 'c11', user: '拿铁妈', avatar: '拿', time: '昨天',
        text: '#宠物友好餐厅 城西那家新开的咖啡店，门口有拴绳桩，店员还会主动给水碗。',
        images: [['HOME', 'building-2', 's-cat-sofa.jpg'], ['PLAY', 'utensils', 's-cat3.jpg'], ['GEAR', 'map-pin', 's-walk.jpg']],
        likes: 152, comments: 23
      }),
      post({
        id: 'c12', user: '阿黎', avatar: '黎', time: '2 天前',
        text: '#换毛季急救 亲测有效：每天五分钟，顺着毛流梳两次，比一次梳十分钟管用。',
        ratio: '1x1',
        images: [['CARE', 'brush', 's-bath.jpg']],
        likes: 203, comments: 37
      })
    ]
  };

  /* ---------- Messages ---------- */
  var notifyCells = [
    { key: 'all', icon: 'bell', label: '全部', unread: true },
    { key: 'interact', icon: 'heart', label: '互动', unread: true },
    { key: 'order', icon: 'truck', label: '订单', unread: true },
    { key: 'system', icon: 'info', label: '系统', unread: true }
  ];

  var conversations = [
    { id: 'm1', name: '拿铁妈', letter: '拿', preview: '好的呀，那周六上午十点公园门口见～', time: '14:20', unread: 1, ava: 's-golden.jpg' },
    { id: 'm2', name: '客服小助手', letter: '客', preview: '您好，请问有什么可以帮您？', time: '昨天', unread: 0, cs: true, ava: 'a-cs.jpg' },
    { id: 'm3', name: '布丁与豆浆', letter: '布', preview: '你家狗狗多大了呀', time: '昨天', unread: 2, ava: 's-cat.jpg' },
    { id: 'm4', name: '阿黎', letter: '黎', preview: '那家店我也去过，环境确实好', time: '周一', unread: 0, ava: 's-cat-sofa.jpg' },
    { id: 'm5', name: '小满', letter: '满', preview: '[图片]', time: '周一', unread: 0, ava: 's-cat3.jpg' },
    { id: 'm6', name: '橘子汽水', letter: '橘', preview: '谢谢你的建议，我去试试看', time: '上周', unread: 0, ava: 's-cat2.jpg' },
    { id: 'm7', name: 'MOYUYO Atelier', letter: 'M', preview: '你的订单已发货，点击查看物流', time: '上周', unread: 0, ava: 'a-brand.jpg' }
  ];

  var conversationsPage2 = [
    { id: 'm8', name: '南风', letter: '南', preview: '想问一下你家用的哪个牌子的梳子', time: '10 月 12 日', unread: 0, ava: 's-dog.jpg' },
    { id: 'm9', name: '橘子', letter: '子', preview: '照片拍得真好看', time: '10 月 9 日', unread: 0, ava: 's-cat2.jpg' },
    { id: 'm10', name: '老周', letter: '周', preview: '下周有空一起带狗去爬山吗', time: '10 月 5 日', unread: 0, ava: 's-bath2.jpg' },
    { id: 'm11', name: 'MOYUYO 售后', letter: 'M', preview: '退款已原路退回，请注意查收', time: '9 月 28 日', unread: 0, cs: true, ava: 'a-brand.jpg' }
  ];

  /* ---------- User ---------- */
  var user = {
    name: '林小满',
    email: 'xiaoman.lin@moyuyo.com',
    level: 'GOLD 会员',
    points: '1,280',
    pointsNext: '再消费 320 元升级 PLATINUM',
    wallet: [
      { key: 'history', icon: 'history', value: '26', label: '浏览历史' },
      { key: 'points', icon: 'award', value: '1,280', label: '积分' },
      { key: 'coupon', icon: 'ticket', value: '3', label: '优惠券' },
      { key: 'collect', icon: 'bookmark', value: '12', label: '收藏' }
    ],
    social: [
      { key: 'follow', value: '12', label: '关注' },
      { key: 'fans', value: '348', label: '粉丝' },
      { key: 'post', value: '8', label: '帖子' },
      { key: 'footprint', value: '45', label: '足迹' }
    ],
    orders: [
      { key: 'unpaid', icon: 'credit-card', label: '待付款', badge: 2 },
      { key: 'unship', icon: 'shopping-cart', label: '待发货', badge: 1 },
      { key: 'unreceive', icon: 'truck', label: '待收货', badge: 0 },
      { key: 'review', icon: 'verified', label: '待评价', badge: 0 }
    ],
    functions: [
      { key: 'checkin', icon: 'check-circle', label: '每日签到', auth: true },
      { key: 'task', icon: 'target', label: '任务中心', auth: true },
      { key: 'invite', icon: 'user-plus', label: '邀请好友', auth: true },
      { key: 'member', icon: 'award', label: '会员中心', auth: true },
      { key: 'address', icon: 'map-pin', label: '收货地址', auth: true },
      { key: 'pets', icon: 'paw-print', label: '我的宠物', auth: true },
      { key: 'collect', icon: 'bookmark', label: '我的收藏', auth: true },
      { key: 'history', icon: 'history', label: '浏览足迹', auth: true },
      { key: 'help', icon: 'help-circle', label: '帮助中心', auth: false },
      { key: 'settings', icon: 'settings', label: '设置', auth: false },
      { key: 'about', icon: 'info', label: '关于我们', auth: false }
    ]
  };

  /* ---------- 占位页去处表（02 §6.4） ---------- */
  var DEST = {
    /* 通用 */
    'go-login':         ['登录 / 注册', '/pages/auth/login'],
    /* Pet */
    'pet-dressup':      ['装扮宠物', '/pages/pet/dressup'],
    'pet-3d':           ['3D 空间', '/pages/pet/pet-hub-3d'],
    'pet-care-wash':    ['洗护记录', '/pages/pet/care-records?type=wash'],
    'pet-care-vaccine': ['疫苗记录', '/pages/pet/care-records?type=vaccine'],
    'pet-care-deworm':  ['驱虫记录', '/pages/pet/care-records?type=deworm'],
    'pet-profile':      ['宠物档案', '/pages/pet/profile'],
    'pet-mall':         ['商品分类', '/pages/tabbar/category'],
    'pet-share':        ['发布晒宠', '/pages/community/publish'],
    'pet-calendar':     ['健康日历', '/pages/pet/health-calendar'],
    'pet-memory':       ['记忆时间轴', '/pages/pet/memory'],
    'pet-weight':       ['体重曲线', '/pages/pet/weight'],
    /* Home */
    'home-search':      ['搜索商品', '/pages/goods/search'],
    'home-category':    ['商品分类', '/pages/tabbar/category'],
    'home-member':      ['会员中心', '/pages/user/member'],
    'home-coupon':      ['领券中心', '/pages/marketing/coupon'],
    'home-flash':       ['限时秒杀', '/pages/marketing/flash-sale'],
    'home-goods':       ['商品详情', '/pages/goods/detail'],
    'home-notify':      ['通知中心', '/pages/user/notifications'],
    /* Community */
    'cm-search':        ['搜索帖子', '/pages/community/search'],
    'cm-notify':        ['通知中心', '/pages/user/notifications'],
    'cm-topic':         ['话题详情', '/pages/community/topic'],
    'cm-author':        ['作者主页', '/pages/user/home'],
    'cm-post':          ['帖子详情', '/pages/community/detail'],
    'cm-publish':       ['发布帖子', '/pages/community/publish'],
    /* Messages */
    'msg-notify':       ['通知中心 · 全部', '/pages/user/notifications?tab=all'],
    'msg-notify-interact': ['通知中心 · 互动', '/pages/user/notifications?tab=interact'],
    'msg-notify-order': ['通知中心 · 订单', '/pages/user/notifications?tab=order'],
    'msg-notify-system':['通知中心 · 系统', '/pages/user/notifications?tab=system'],
    'msg-group':        ['发起群聊', '/pages/message/create-group'],
    'msg-dm':           ['发起私信', '/pages/message/followers'],
    'msg-conv':         ['私信会话', '/pages/message/chat'],
    'msg-followers':    ['关注列表', '/pages/message/followers'],
    /* User */
    'us-profile':       ['个人资料', '/pages/user/profile'],
    'us-member':        ['会员中心', '/pages/user/member'],
    'us-wallet-history':['浏览历史', '/pages/user/history'],
    'us-wallet-points': ['我的积分', '/pages/user/points'],
    'us-wallet-coupon': ['我的优惠券', '/pages/user/coupon'],
    'us-wallet-collect':['我的收藏', '/pages/user/favorites'],
    'us-social-follow': ['我的关注', '/pages/user/follow'],
    'us-social-fans':   ['我的粉丝', '/pages/user/fans'],
    'us-social-fav':    ['我的收藏', '/pages/user/favorites'],
    'us-social-post':   ['我的帖子', '/pages/user/posts'],
    'us-order-all':     ['全部订单', '/pages/order/list'],
    'us-order-unpaid':  ['待付款订单', '/pages/order/list?type=unpaid'],
    'us-order-unship':  ['待发货订单', '/pages/order/list?type=unship'],
    'us-order-unreceive':['待收货订单', '/pages/order/list?type=unreceive'],
    'us-order-review':  ['待评价订单', '/pages/order/list?type=review'],
    'us-cart':          ['购物车', '/pages/goods/cart'],
    'us-fn-checkin':    ['每日签到', '/pages/user/checkin'],
    'us-fn-task':       ['任务中心', '/pages/user/task'],
    'us-fn-invite':     ['邀请好友', '/pages/user/invite'],
    'us-fn-member':     ['会员中心', '/pages/user/member'],
    'us-fn-address':    ['收货地址', '/pages/user/address'],
    'us-fn-pets':       ['我的宠物', '/pages/pet/list'],
    'us-fn-collect':    ['我的收藏', '/pages/user/favorites'],
    'us-fn-history':    ['浏览足迹', '/pages/user/history'],
    'us-fn-help':       ['帮助中心', '/pages/user/help'],
    'us-fn-settings':   ['设置', '/pages/user/settings'],
    'us-fn-about':      ['关于我们', '/pages/user/about']
  };

  w.MOYUYO_DATA = {
    LINE: LINE,
    pets: pets,
    petQuick: petQuick,
    homeQuick: homeQuick,
    banners: banners,
    homeTabs: homeTabs,
    products: products,
    topics: topics,
    communityTabs: communityTabs,
    posts: posts,
    notifyCells: notifyCells,
    conversations: conversations,
    conversationsPage2: conversationsPage2,
    user: user,
    DEST: DEST
  };
})(window);
