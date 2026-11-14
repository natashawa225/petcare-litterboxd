import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

fig, ax = plt.subplots(figsize=(6.4, 6.0), dpi=220)
ax.set_xlim(0, 10)
ax.set_ylim(0, 9.4)
ax.axis("off")

LAYER_COLORS = {
    "presentation": "#dbe9f6",
    "domain": "#e4f1df",
    "data": "#fbe7cf",
    "cross": "#efe3f6",
    "external": "#f6d8d8",
}

def box(x, y, w, h, text, color, fontsize=9.5, weight="normal"):
    rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.04,rounding_size=0.08",
                           linewidth=0.9, edgecolor="#333333", facecolor=color)
    ax.add_patch(rect)
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=fontsize,
             weight=weight, linespacing=1.25)
    return (x, y, w, h)

def layer_label(x, y, text):
    ax.text(x, y, text, ha="left", va="center", fontsize=9.5, weight="bold", color="#222222")

def arrow(b1, b2, side1="bottom", side2="top", color="#444444", style="-|>"):
    def pt(b, side):
        x, y, w, h = b
        if side == "bottom": return (x + w / 2, y)
        if side == "top": return (x + w / 2, y + h)
        if side == "left": return (x, y + h / 2)
        if side == "right": return (x + w, y + h / 2)
    p1, p2 = pt(b1, side1), pt(b2, side2)
    ax.add_patch(FancyArrowPatch(p1, p2, arrowstyle=style, mutation_scale=10,
                                  linewidth=1.0, color=color, shrinkA=2, shrinkB=2))

ax.text(5.0, 9.2, "Figure 1. Litterboxd layered architecture", ha="center", va="center",
        fontsize=11, weight="bold")

# --- Presentation layer ---
layer_label(0.2, 8.55, "Presentation — Activities, XML layouts")
p_daily = box(0.3, 7.5, 2.2, 0.8, "Daily", LAYER_COLORS["presentation"])
p_tasks = box(2.7, 7.5, 2.2, 0.8, "Tasks", LAYER_COLORS["presentation"])
p_diary = box(5.1, 7.5, 2.2, 0.8, "Diary", LAYER_COLORS["presentation"])
p_profile = box(7.5, 7.5, 2.2, 0.8, "Profile", LAYER_COLORS["presentation"])

# --- Domain layer ---
layer_label(0.2, 6.75, "Domain — plain Java, unit-tested")
d_checkin = box(0.6, 5.7, 2.9, 0.8, "Check-in engine", LAYER_COLORS["domain"])
d_health = box(3.8, 5.7, 2.9, 0.8, "Health rules engine", LAYER_COLORS["domain"])
d_tasks = box(7.0, 5.7, 2.7, 0.8, "Task planner", LAYER_COLORS["domain"])

# --- Data layer ---
layer_label(0.2, 4.95, "Data")
db_sqlite = box(0.6, 3.9, 4.9, 0.8, "SQLite (DAOs, schema v7)", LAYER_COLORS["data"])
db_files = box(5.8, 3.9, 3.9, 0.8, "Files / Prefs", LAYER_COLORS["data"])

# --- Cross-cutting ---
layer_label(0.2, 3.15, "Cross-cutting")
exec_box = box(0.6, 2.1, 3.4, 0.8, "Executors", LAYER_COLORS["cross"])
rem_box = box(4.4, 2.1, 3.9, 0.8, "Reminders (AlarmManager)", LAYER_COLORS["cross"])

# --- External ---
layer_label(0.2, 1.35, "External")
ext_box = box(2.4, 0.3, 4.6, 0.8, "icatcare.org (live search)", LAYER_COLORS["external"])

# Arrows
arrow(p_daily, d_health, "bottom", "top")
arrow(p_tasks, d_tasks, "bottom", "top")
arrow(p_diary, db_sqlite, "bottom", "top")
arrow(p_profile, db_sqlite, "bottom", "top")
arrow(p_daily, exec_box, "left", "left", color="#555555")
arrow(d_checkin, db_sqlite, "bottom", "top")
arrow(d_health, db_sqlite, "bottom", "top")
arrow(d_tasks, db_sqlite, "bottom", "top")
arrow(p_tasks, rem_box, "bottom", "top")
arrow(rem_box, db_sqlite, "left", "right")
arrow(p_profile, db_files, "bottom", "top")
arrow(d_health, ext_box, "bottom", "top", color="#883333")

plt.tight_layout()
plt.savefig("architecture_diagram.png", bbox_inches="tight", facecolor="white")
print("saved architecture_diagram.png")
