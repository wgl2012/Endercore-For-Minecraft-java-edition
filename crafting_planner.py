import json
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format="[CraftingPlanner] %(message)s")

class CraftingPlanner:
    def __init__(self):
        # 合成配方库
        self.recipes = {}
        # 基础物品（无法合成的物品）
        self.base_items = {"木头", "原木", "石头", "铁矿石", "金矿石", "钻石矿石", "煤炭", "红石", "下界石英"}

    def load_recipes(self, recipe_config=None):
        """加载合成配方"""
        if recipe_config:
            self.recipes = recipe_config
            logging.info(f"加载了 {len(self.recipes)} 个合成配方")
            return
        
        # 默认配方（基础示例）
        self.recipes = {
            "木板": {
                "input": {"原木": 1},
                "output": {"木板": 4},
                "type": "基础合成"
            },
            "木棍": {
                "input": {"木板": 2},
                "output": {"木棍": 4},
                "type": "基础合成"
            },
            "木剑": {
                "input": {"木板": 2, "木棍": 1},
                "output": {"木剑": 1},
                "type": "基础合成"
            },
            "木镐": {
                "input": {"木板": 3, "木棍": 2},
                "output": {"木镐": 1},
                "type": "基础合成"
            },
            "工作台": {
                "input": {"木板": 4},
                "output": {"工作台": 1},
                "type": "基础合成"
            }
        }
        logging.info(f"加载默认配方，共 {len(self.recipes)} 个")

    def get_item_requirements(self, item, count=1):
        """获取合成指定物品的原料需求"""
        if item in self.base_items:
            return {item: count}
        
        if item not in self.recipes:
            logging.warning(f"未知物品：{item}，无法合成")
            return None
        
        recipe = self.recipes[item]
        input_items = recipe["input"]
        output_count = recipe["output"][item]
        
        # 计算需要合成多少次
        craft_times = (count + output_count - 1) // output_count
        
        requirements = {}
        for req_item, req_count in input_items.items():
            # 递归获取原料的需求
            sub_requirements = self.get_item_requirements(req_item, req_count * craft_times)
            if not sub_requirements:
                return None
            
            # 合并需求
            for s_item, s_count in sub_requirements.items():
                if s_item in requirements:
                    requirements[s_item] += s_count
                else:
                    requirements[s_item] = s_count
        
        return requirements

    def get_craft_plan(self, target_item, current_items=None, target_count=1):
        """获取合成规划路径"""
        if current_items is None:
            current_items = {}
        
        # 获取总需求
        total_requirements = self.get_item_requirements(target_item, target_count)
        if not total_requirements:
            return None
        
        # 计算缺少的物品
        missing_items = {}
        for item, count in total_requirements.items():
            current_count = current_items.get(item, 0)
            if current_count < count:
                missing_items[item] = count - current_count
        
        if not missing_items:
            # 物品足够，直接合成目标
            return [f"合成 {target_count}个{target_item}"]
        
        # 生成合成计划
        craft_plan = []
        
        # 先收集基础物品
        for item, count in missing_items.items():
            if item in self.base_items:
                craft_plan.append(f"收集 {count}个{item}")
        
        # 然后合成中间物品
        # 简化版：按配方依赖顺序生成
        craft_order = self._get_craft_order(target_item)
        for item in craft_order[:-1]:  # 排除最后一个（目标物品）
            if item in missing_items:
                craft_plan.append(f"合成 {missing_items[item]}个{item}")
        
        # 最后合成目标物品
        craft_plan.append(f"合成 {target_count}个{target_item}")
        
        return craft_plan

    def _get_craft_order(self, item):
        """获取合成顺序（递归）"""
        if item in self.base_items:
            return [item]
        
        if item not in self.recipes:
            return []
        
        order = []
        recipe = self.recipes[item]
        
        # 先获取原料的合成顺序
        for input_item in recipe["input"].keys():
            order.extend(self._get_craft_order(input_item))
        
        # 去重并保持顺序
        unique_order = []
        for i in order:
            if i not in unique_order:
                unique_order.append(i)
        
        # 添加当前物品
        unique_order.append(item)
        
        return unique_order

    def save_recipes(self, file_path):
        """保存配方到文件"""
        try:
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(self.recipes, f, ensure_ascii=False, indent=4)
            logging.info(f"配方已保存到：{file_path}")
            return True
        except Exception as e:
            logging.error(f"保存配方失败：{str(e)}")
            return False

# 测试代码
if __name__ == "__main__":
    planner = CraftingPlanner()
    planner.load_recipes()
    
    # 测试合成木剑
    current_items = {"原木": 1}
    plan = planner.get_craft_plan("木剑", current_items)
    print("合成木剑计划：", plan)
    
    # 测试合成木镐
    plan2 = planner.get_craft_plan("木镐", current_items)
    print("合成木镐计划：", plan2)
