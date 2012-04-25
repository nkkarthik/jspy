package spy;

import javax.swing.tree.*;

public class Utils {
    
    public static TreePath 
        nodeToPath(DefaultMutableTreeNode node) {
        
        return new TreePath(node.getPath());
    }

}
